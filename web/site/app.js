// Page logic: talks to worker.js, renders the transcript and the file browser.
const $ = (id) => document.getElementById(id);
const transcript = $('transcript');
const form = $('form');
const input = $('input');
const runBtn = $('run');
const stopBtn = $('stop');
const status = $('status');
const treeEl = $('tree');
const viewer = $('viewer');

const HISTORY_KEY = 'walnut.history';
const THEME_KEY = 'walnut.theme';
const OPEN_DIRS_KEY = 'walnut.openDirs';
const HOME_PREFIX = '/walnut/'; // the prover's internal home directory, stripped from paths in output

let worker = null;
let ready = false;
let running = null; // { entry, outEl, started, timer }
let nextId = 1;
const pending = new Map(); // id -> resolve
let history = load(HISTORY_KEY, []);
let historyPos = history.length;
let draft = '';
let openDirs = new Set(load(OPEN_DIRS_KEY, ['Result', 'Automata Library']));
let currentFile = null;

function load(key, fallback) {
  try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; } catch { return fallback; }
}
function save(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage may be blocked */ }
}

// ---- theme ------------------------------------------------------------------------------------
function applyTheme(t) {
  document.documentElement.dataset.theme = t === 'dark' ? 'dark' : 'light';
}
applyTheme(load(THEME_KEY, 'light'));
$('theme').addEventListener('click', () => {
  const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  save(THEME_KEY, next);
  applyTheme(next);
});

// ---- worker -----------------------------------------------------------------------------------
function setStatus(state, text) {
  status.dataset.state = state;
  status.textContent = text;
}

function startWorker() {
  ready = false;
  setStatus('loading', 'Loading prover');
  worker = new Worker(`./worker.js${location.search}`, { type: 'module' });
  worker.onmessage = onWorkerMessage;
  worker.onerror = (e) => {
    setStatus('error', 'Prover crashed');
    finishRun({ error: e.message || 'The prover stopped unexpectedly.' });
  };
}

function call(type, payload = {}) {
  return new Promise((resolve, reject) => {
    const id = nextId++;
    pending.set(id, { resolve, reject });
    worker.postMessage({ type, id, ...payload });
  });
}

function onWorkerMessage(event) {
  const msg = event.data;
  switch (msg.type) {
    case 'ready':
      ready = true;
      setStatus('ready', 'Ready');
      $('version').textContent = `v${msg.version}`;
      renderTree(msg.tree);
      if (msg.restored > 0) addNote(`Restored ${msg.restored} saved file${msg.restored === 1 ? '' : 's'} from this browser.`);
      input.focus();
      break;
    case 'out':
      appendOutput(msg.text);
      break;
    case 'done':
      renderTree(msg.tree);
      finishRun(msg);
      resolvePending(msg);
      break;
    case 'file': case 'written': case 'removed': case 'reset':
      if (msg.tree) renderTree(msg.tree);
      resolvePending(msg);
      break;
    case 'failure':
      if (msg.id) { const p = pending.get(msg.id); pending.delete(msg.id); p?.reject(new Error(msg.error)); }
      else { setStatus('error', 'Prover failed to start'); addNote(`The prover failed to start: ${msg.error}`, true); }
      break;
  }
}
function resolvePending(msg) {
  const p = pending.get(msg.id);
  if (p) { pending.delete(msg.id); p.resolve(msg); }
}

// ---- transcript -------------------------------------------------------------------------------
function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined) e.textContent = text;
  return e;
}
function scrollToBottom() { transcript.scrollTop = transcript.scrollHeight; }

function addNote(text, isError = false) {
  const entry = el('div', `entry note${isError ? ' error' : ''}`);
  entry.appendChild(el('p', null, text));
  transcript.appendChild(entry);
  scrollToBottom();
}

// The clear/cls command prints the terminal escape sequence for "erase screen"; emulate it.
const CLEAR_SCREEN = /\x1b\[H|\x1b\[2J/g;
function clearTranscript() {
  for (const child of [...transcript.children]) {
    if (!running || child !== running.entry) child.remove();
  }
  if (running) { running.outEl.textContent = ''; running.partial = ''; running.pendingRule = false; running.group = null; }
}

// ---- output rendering --------------------------------------------------------------------------
// Output is rendered line by line into typed elements. The patterns below match formats that each
// come from one place in the prover: eval results ("____" then TRUE/FALSE), evaluation steps
// ("expr:N states - T ms", indented by depth), verbose steps ("computing X"), the total-time
// footer, error messages and Java stack traces, and the commands echoed by "load". The output of
// "test" and "help" is recognized by the command that produced it.
const VERDICT = /^(TRUE|FALSE)$/;
const STEP = /^(\s*)(.+?):\s*(\d+) states(?: - (\d+)(ms| states))?\.?$/;
const VERBOSE = /^(\s*)(computing|computed|comparing|compared|quantifying|quantified|fixing|fixed|totalizing|totalized|Minimizing|Determinizing|Applying|Calculating) (.*)$/;
const TOTAL = /^Total computation time: (\d+)ms\.?$/;
const ERROR = /(: char at \d+$|^File does not exist: |^Undefined token|^Unbalanced|^Metacommands |^No such command|^Invalid command|^Operator .* requires|^operator .* requires|^Mismatch |^Macro does not exist|^Automaton .* does not|^A morphism |^[\w.$]*(Exception|Error)\b)/;
const FRAME = /^\s+at [\w.$<>/]+\(/;
const COMMAND_WORDS = 'eval|def|macro|reg|load|ost|exit|quit|cls|clear|combine|morphism|promote|image|inf|split|rsplit|join|test|transduce|reverse|minimize|convert|fixleadzero|fixtrailzero|alphabet|union|intersect|star|concat|rightquo|leftquo|describe|export|help';
const ECHOED_COMMAND = new RegExp(`^(?:\\[[^\\]]*\\]\\s*)*(?:${COMMAND_WORDS})\\b.*[;:]$`);
const HELP_TITLE = /^=== (.*) ===$/;
const RULE = /^=+$/;
const BIG_STATES = 100000;

function addLine(cls, text) {
  const line = el('div', cls ? `line ${cls}` : 'line', text);
  running.outEl.appendChild(line);
  running.group = null;
  return line;
}

function stepGroup() {
  if (!running.group) {
    const details = el('details', 'steps');
    details.open = true;
    details.appendChild(el('summary', null, 'steps'));
    running.outEl.appendChild(details);
    running.group = { details, count: 0 };
  }
  running.group.count++;
  running.group.details.firstChild.textContent = `${running.group.count} step${running.group.count === 1 ? '' : 's'}`;
  return running.group.details;
}

function addStep(depth, label, states, extra, unit, verbose) {
  const line = el('div', `line step${verbose ? ' verbose' : ''}`);
  line.style.paddingLeft = `${depth * 16}px`;
  if (depth) line.style.backgroundSize = `${depth * 16}px 100%`;
  line.appendChild(el('span', 'expr', label));
  if (states !== undefined) {
    const n = Number(states);
    const stat = el('span', `stat${n >= BIG_STATES ? ' big' : ''}`);
    stat.append(`${n.toLocaleString()} state${n === 1 ? '' : 's'}`);
    if (extra !== undefined) stat.append(unit === 'ms' ? ` · ${extra} ms` : ` × ${Number(extra).toLocaleString()} states`);
    line.appendChild(stat);
  }
  stepGroup().appendChild(line);
}

function addFrame(text) {
  const last = running.outEl.lastChild;
  let frames = last && last.classList && last.classList.contains('frames') ? last : null;
  if (!frames) {
    frames = el('details', 'frames');
    frames.appendChild(el('summary', null, 'stack trace'));
    running.outEl.appendChild(frames);
    running.group = null;
  }
  frames.appendChild(el('div', 'line', text.trim()));
}

function addTestValue(text) {
  const last = running.outEl.lastChild;
  let row = last && last.classList && last.classList.contains('test-values') ? last : null;
  if (!row) { row = el('div', 'test-values'); running.outEl.appendChild(row); running.group = null; }
  row.appendChild(el('span', 'chip', text));
  row.dataset.count = row.childElementCount;
}

function emitLine(line) {
  if (running.pendingRule) {
    running.pendingRule = false;
    if (VERDICT.test(line)) {
      addLine('verdict-line').appendChild(el('span', `verdict ${line.toLowerCase()}`, line));
      running.verdicts++;
      if (running.verdicts <= 4) running.brief.appendChild(el('span', `verdict ${line.toLowerCase()}`, line));
      else if (running.verdicts === 5) running.brief.appendChild(el('span', 'more', '…'));
      return;
    }
    addLine(null, '____');
  }
  if (line === '____') { running.pendingRule = true; return; }
  const cleaned = line.split(HOME_PREFIX).join('');
  let m;
  if (FRAME.test(line)) { addFrame(line); return; }
  if (ERROR.test(cleaned)) { addLine('error', cleaned.trim()); return; }
  if (running.kind === 'help') {
    if ((m = HELP_TITLE.exec(line))) { addLine('help-title', m[1]); return; }
    if (RULE.test(line)) return;
    addLine(/^\t/.test(line) ? 'help-code' : 'help-text', line.replace(/^\t/, ''));
    return;
  }
  if ((m = TOTAL.exec(line))) { addLine('total', `Prover time ${m[1]} ms`); return; }
  if ((m = STEP.exec(line))) { addStep(m[1].length, m[2], m[3], m[4], m[5], false); return; }
  if ((m = VERBOSE.exec(line))) { addStep(m[1].length, `${m[2]} ${m[3]}`, undefined, undefined, undefined, true); return; }
  if (ECHOED_COMMAND.test(line)) { addLine('cmd', line); return; }
  if (running.kind === 'test') { if (line.trim()) addTestValue(line.trim()); return; }
  addLine(null, line);
}

function renderChunk(text) {
  const lines = (running.partial + text).split('\n');
  running.partial = lines.pop();
  for (const line of lines) emitLine(line);
}

function flushOutput() {
  if (running.pendingRule) { running.pendingRule = false; addLine(null, '____'); }
  if (running.partial) { emitLine(running.partial); running.partial = ''; }
}

function appendOutput(text) {
  if (text.includes('\x1b[2J')) {
    clearTranscript();
    text = text.replace(CLEAR_SCREEN, '');
    if (!text.trim()) return;
  }
  if (!running) { addNote(text); return; }
  // The prover echoes the command as its first line; the transcript already shows it.
  if (!running.echoStripped) {
    running.echoStripped = true;
    const firstLine = text.split('\n', 1)[0];
    if (running.command.split('\n').join('') === firstLine || running.command.startsWith(firstLine)) {
      text = text.slice(firstLine.length).replace(/^\n/, '');
      if (!text) return;
    }
  }
  renderChunk(text);
  const isNearBottom = transcript.scrollHeight - transcript.scrollTop - transcript.clientHeight < 80;
  if (isNearBottom) scrollToBottom();
}

function beginRun(command) {
  const entry = el('details', 'entry');
  entry.open = true;
  const head = el('summary', 'head');
  head.appendChild(el('span', 'cmd', command));
  const brief = el('span', 'brief');
  head.appendChild(brief);
  entry.appendChild(head);
  const outEl = el('div', 'out');
  entry.appendChild(outEl);
  const loader = el('div', 'working');
  loader.appendChild(el('span', 'spinner'));
  loader.appendChild(el('span', 'working-text', 'Running'));
  entry.appendChild(loader);
  transcript.appendChild(entry);
  updateFoldLabel();
  scrollToBottom();
  const kind = (command.match(/^(?:\[[^\]]*\]\s*)*(\w+)/) || [])[1] || '';
  running = { entry, outEl, brief, loader, command, kind, started: performance.now(), echoStripped: false, partial: '', pendingRule: false, group: null, verdicts: 0 };
  running.timer = setInterval(() => {
    const secs = ((performance.now() - running.started) / 1000).toFixed(0);
    setStatus('busy', `Running, ${secs} s`);
    loader.lastChild.textContent = `Running, ${secs} s`;
  }, 1000);
  setStatus('busy', 'Running');
  runBtn.hidden = true;
  stopBtn.hidden = false;
  input.disabled = true;
}

function finishRun(msg) {
  if (!running) return;
  clearInterval(running.timer);
  const { entry, outEl, brief, loader } = running;
  const ms = msg.ms ?? (performance.now() - running.started);
  flushOutput();
  loader.remove();
  if (msg.error) outEl.appendChild(el('pre', 'err', msg.error));
  brief.appendChild(el('span', 'meta', ms >= 1000 ? `${(ms / 1000).toFixed(1)} s` : `${Math.round(ms)} ms`));
  const changed = msg.changes ? msg.changes.dirty.filter((p) => !p.endsWith('_log.txt') && p !== 'Result/global_log.txt') : [];
  if (changed.length) {
    const line = el('div', 'files-changed');
    line.append('Wrote ');
    changed.forEach((p, i) => {
      if (i) line.append(', ');
      const b = el('button', null, p);
      b.type = 'button';
      b.addEventListener('click', () => openFile(p));
      line.appendChild(b);
    });
    outEl.appendChild(line);
  }
  if (!outEl.childElementCount) entry.classList.add('empty');
  running = null;
  runBtn.hidden = false;
  stopBtn.hidden = true;
  input.disabled = false;
  setStatus(ready ? 'ready' : 'error', ready ? 'Ready' : 'Prover stopped');
  scrollToBottom();
  if (msg.exited) addNote('Session ended with exit. Reload the page to start again, your files are kept.');
  else input.focus();
}

// ---- input ------------------------------------------------------------------------------------
function autosize() {
  input.style.height = 'auto';
  input.style.height = `${Math.min(input.scrollHeight, window.innerHeight * 0.4)}px`;
}
input.addEventListener('input', autosize);

function isComplete(text) {
  const t = text.trim();
  return t.endsWith(';') || t.endsWith(':');
}

input.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    if (isComplete(input.value)) { e.preventDefault(); form.requestSubmit(); }
    return;
  }
  const single = !input.value.includes('\n');
  if (e.key === 'ArrowUp' && single && historyPos > 0) {
    e.preventDefault();
    if (historyPos === history.length) draft = input.value;
    historyPos--;
    input.value = history[historyPos];
    autosize();
  } else if (e.key === 'ArrowDown' && single && historyPos < history.length) {
    e.preventDefault();
    historyPos++;
    input.value = historyPos === history.length ? draft : history[historyPos];
    autosize();
  }
});

form.addEventListener('submit', (e) => {
  e.preventDefault();
  const text = input.value.trim();
  if (!text || !ready || running) return;
  if (!isComplete(text)) { addNote('Commands must end with ; or :', true); return; }
  if (history[history.length - 1] !== text) { history.push(text); history = history.slice(-200); save(HISTORY_KEY, history); }
  historyPos = history.length;
  draft = '';
  input.value = '';
  autosize();
  beginRun(text);
  worker.postMessage({ type: 'run', id: nextId++, text });
});

const foldBtn = $('fold');
function updateFoldLabel() {
  const boxes = transcript.querySelectorAll('details.entry');
  const anyOpen = [...boxes].some((d) => d.open);
  foldBtn.textContent = anyOpen ? 'Collapse outputs' : 'Expand outputs';
  foldBtn.hidden = boxes.length === 0;
}
foldBtn.addEventListener('click', () => {
  const boxes = [...transcript.querySelectorAll('details.entry')];
  const anyOpen = boxes.some((d) => d.open);
  for (const d of boxes) d.open = !anyOpen;
  updateFoldLabel();
});
transcript.addEventListener('toggle', updateFoldLabel, true);

stopBtn.addEventListener('click', () => {
  if (!running) return;
  worker.terminate();
  finishRun({ error: 'Stopped. Files written by this command were not saved.' });
  addNote('Restarting the prover.');
  startWorker();
});

// ---- files ------------------------------------------------------------------------------------
function renderTree(tree) {
  treeEl.replaceChildren();
  for (const [dir, files] of Object.entries(tree)) {
    const details = el('details');
    details.open = openDirs.has(dir);
    details.addEventListener('toggle', () => {
      if (details.open) openDirs.add(dir); else openDirs.delete(dir);
      save(OPEN_DIRS_KEY, [...openDirs]);
    });
    const summary = el('summary');
    summary.append(el('span', null, dir), el('span', 'count', String(files.length)));
    details.appendChild(summary);
    const ul = el('ul');
    if (files.length === 0) ul.appendChild(el('li', 'empty', 'Empty'));
    for (const f of files) {
      const li = el('li');
      const b = el('button', null, f);
      b.type = 'button';
      b.addEventListener('click', () => openFile(`${dir}/${f}`));
      li.appendChild(b);
      ul.appendChild(li);
    }
    details.appendChild(ul);
    treeEl.appendChild(details);
  }
}

async function openFile(path) {
  const { content } = await call('read', { path });
  if (content === null) { addNote(`${path} does not exist.`, true); return; }
  currentFile = { path, content };
  $('viewer-name').textContent = path;
  $('viewer-text').textContent = content;
  treeEl.hidden = true;
  viewer.hidden = false;
  const graph = $('viewer-graph');
  graph.hidden = true;
  graph.replaceChildren();
  if (path.endsWith('.gv')) renderGraph(content, graph);
}

$('viewer-back').addEventListener('click', () => { viewer.hidden = true; treeEl.hidden = false; currentFile = null; });

$('viewer-download').addEventListener('click', () => {
  if (!currentFile) return;
  const blob = new Blob([currentFile.content], { type: 'text/plain' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = currentFile.path.split('/').pop();
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
});

$('viewer-delete').addEventListener('click', async () => {
  if (!currentFile) return;
  if (!(await confirm(`Delete ${currentFile.path}? This cannot be undone.`, 'Delete'))) return;
  await call('remove', { path: currentFile.path });
  $('viewer-back').click();
});

$('import').addEventListener('click', () => $('import-input').click());
$('import-input').addEventListener('change', async (e) => {
  const files = [...e.target.files];
  e.target.value = '';
  if (!files.length) return;
  const dir = await chooseDirectory();
  if (!dir) return;
  for (const f of files) {
    const content = await f.text();
    await call('write', { path: `${dir}/${f.name}`, content });
  }
  addNote(`Added ${files.length} file${files.length === 1 ? '' : 's'} to ${dir}.`);
});

$('reset').addEventListener('click', async () => {
  if (!(await confirm('Remove every file you created or changed and restore the default libraries? Your command history is kept.', 'Reset'))) return;
  if (running) worker.terminate();
  await call('reset').catch(() => {});
  worker.terminate();
  transcript.replaceChildren();
  updateFoldLabel();
  addNote('Reset to the default libraries.');
  startWorker();
});

function confirm(text, okLabel) {
  const dialog = $('confirm');
  $('confirm-text').textContent = text;
  $('confirm-ok').textContent = okLabel;
  return new Promise((resolve) => {
    dialog.addEventListener('close', () => resolve(dialog.returnValue === 'ok'), { once: true });
    dialog.showModal();
  });
}

function chooseDirectory() {
  const dirs = [...treeEl.querySelectorAll('details > summary > span:first-child')].map((s) => s.textContent);
  const choice = window.prompt(`Add to which folder?\n${dirs.map((d, i) => `${i + 1}. ${d}`).join('\n')}`, '1');
  if (choice === null) return null;
  const idx = Number(choice) - 1;
  return dirs[idx] ?? null;
}

// Graphviz rendering for .gv files, loaded on demand.
let vizPromise = null;
async function renderGraph(dot, container) {
  container.hidden = false;
  container.appendChild(el('p', 'note', 'Rendering graph'));
  try {
    if (!vizPromise) {
      vizPromise = import('https://cdn.jsdelivr.net/npm/@viz-js/viz@3.11.0/lib/viz-standalone.mjs').then((m) => m.instance());
    }
    const viz = await vizPromise;
    const svg = viz.renderSVGElement(dot);
    themeGraph(svg);
    container.replaceChildren(svg);
  } catch (e) {
    container.replaceChildren(el('p', 'note', `Graph could not be rendered (${e.message}). The source is shown below.`));
  }
}

// Graphviz hard-codes black strokes and a white background; make them follow the page theme.
function themeGraph(svg) {
  svg.style.color = 'var(--fg)';
  for (const node of svg.querySelectorAll('*')) {
    if (node.getAttribute('stroke') === 'black') node.setAttribute('stroke', 'currentColor');
    if (node.getAttribute('fill') === 'black') node.setAttribute('fill', 'currentColor');
    if (node.getAttribute('fill') === 'white') node.setAttribute('fill', 'none');
    if (node.tagName === 'text' && !node.hasAttribute('fill')) node.setAttribute('fill', 'currentColor');
  }
}

startWorker();
autosize();
