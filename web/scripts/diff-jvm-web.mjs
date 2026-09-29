#!/usr/bin/env node
// Differential test: runs the same commands through the JVM build and the browser build, then
// compares (1) the console output of every command and (2) every file either build produced,
// including logs. Timings and absolute paths are normalized away.
// Usage: node web/scripts/diff-jvm-web.mjs [command-file ...]   (defaults to the integration corpus)
import { spawnSync } from 'node:child_process';
import { mkdirSync, rmSync, readdirSync, readFileSync, writeFileSync, cpSync, existsSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const corpus = join(root, 'src', 'test', 'resources', 'integrationTests', 'Global');
const work = join(process.env.WALNUT_DIFF_DIR ?? tmpdir(), 'walnut-diff');
rmSync(work, { recursive: true, force: true });
mkdirSync(work, { recursive: true });

// Commands: the same list IntegrationTest uses, plus every command file in the corpus.
const commandFiles = process.argv.slice(2).length
  ? process.argv.slice(2)
  : readdirSync(join(corpus, 'Command Files')).filter((f) => f.endsWith('.txt'));
const commands = [
  'reg endsIn2Zeros lsd_2 "(0|1)*00";',
  'reg startsWith2Zeros msd_2 "00(0|1)*";',
  'def thueeq "T[x]=T[y]";',
  'def thuefactoreq "Ak (k < n) => T[i+k] = T[j+k]";',
  ...commandFiles.map((f) => `load ${f};`),
  'exit;',
];

// ---- JVM ---------------------------------------------------------------------------------------
const jvmHome = join(work, 'jvm');
cpSync(corpus, jvmHome, { recursive: true });
for (const d of ['Result', 'Session']) mkdirSync(join(jvmHome, d), { recursive: true });
const script = commands.join('\n') + '\n';
const jar = join(root, 'target', 'Walnut-all.jar');
const t0 = Date.now();
// Some corpus files reference automata that a later file defines, so a few commands fail in both
// builds. That is fine as long as both builds fail identically, which the per-command comparison
// below checks. Stack traces go to stderr and are merged in.
const jvm = spawnSync('java', ['-jar', jar, `--home-dir=${jvmHome}/`, '--global-session'],
  { input: script, maxBuffer: 1 << 28, encoding: 'utf8' });
if (jvm.status !== 0) { console.log(jvm.stderr); throw new Error(`JVM run failed with status ${jvm.status}`); }
console.log(`JVM run: ${Date.now() - t0} ms`);
// The JVM reads stdin in console mode: it prints a prompt before each command and does not echo
// the command. Split its output at the prompts to get one chunk per command.
const PROMPT = '\n[Walnut]$ ';
const jvmChunks = jvm.stdout.split(PROMPT).slice(1, 1 + commands.length);
// Uncaught-exception traces go to stderr on the JVM (so they cannot be attributed to a command)
// and to the shared output sink in the browser. Their class names and JDK-specific messages are
// dropped by normalize(); the Walnut-level error messages that follow them are still compared.

// ---- web ---------------------------------------------------------------------------------------
const W = await import(join(root, 'web', 'app', 'target', 'site', 'walnut.js'));
let webOutput = '';
W.setOutput((text) => { webOutput += text; });
const HOME = W.home();
function seed(dir, rel = '') {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) seed(p, `${rel}${name}/`);
    else if (name.endsWith('.txt')) W.writeFile(HOME + rel + name, readFileSync(p, 'utf8'));
  }
}
seed(corpus);
W.init();
const t1 = Date.now();
const webChunks = [];
for (const c of commands) {
  webOutput = '';
  W.run(c);
  // run() echoes the command as its first line; the JVM console mode does not.
  webChunks.push(webOutput.startsWith(c + '\n') ? webOutput.slice(c.length + 1) : webOutput);
}
console.log(`Web run: ${Date.now() - t1} ms`);

// ---- compare console output per command ---------------------------------------------------------
const IGNORE = /(\d+ms|Total computation time.*|Applying valid representation.*)/g;
const TRACE_LINE = /^(\tat |    at |[\w.$]*(Exception|Error)\b)/;
const normalize = (s) => s.replace(IGNORE, '').replace(/\r\n/g, '\n')
  .split(jvmHome + '/').join('HOME/').split(HOME).join('HOME/')
  .split('\n').filter((l) => !TRACE_LINE.test(l)).join('\n').trim();
let commandMismatches = 0;
commands.forEach((c, i) => {
  const a = normalize(jvmChunks[i] ?? '');
  const b = normalize(webChunks[i] ?? '');
  if (a !== b) {
    commandMismatches++;
    const out = join(work, 'diff', 'commands');
    mkdirSync(out, { recursive: true });
    writeFileSync(join(out, `${i}.jvm`), `${c}\n${a}`);
    writeFileSync(join(out, `${i}.web`), `${c}\n${b}`);
    console.log(`OUTPUT DIFFERS for command ${i}: ${c}`);
  }
});
console.log(`${commands.length} commands compared, ${commandMismatches} with different output`);
if (commandMismatches) process.exitCode = 1;

// ---- compare produced files, logs included ---------------------------------------------------
let compared = 0; let mismatches = 0;
function walk(dir, rel = '') {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) { walk(p, `${rel}${name}/`); continue; }
    if (!name.endsWith('.txt') && !name.endsWith('.gv')) continue;
    const webContent = W.readFile(HOME + rel + name);
    compared++;
    if (webContent === null) { mismatches++; console.log(`MISSING in web: ${rel}${name}`); continue; }
    const a = normalize(readFileSync(p, 'utf8')); const b = normalize(webContent);
    if (a !== b) {
      mismatches++;
      console.log(`DIFFERS: ${rel}${name}`);
      const out = join(work, 'diff', rel);
      mkdirSync(out, { recursive: true });
      writeFileSync(join(out, `${name}.jvm`), a);
      writeFileSync(join(out, `${name}.web`), b);
    }
  }
}
for (const d of ['Result', 'Automata Library', 'Word Automata Library', 'Custom Bases', 'Macro Library', 'Morphism Library']) {
  if (existsSync(join(jvmHome, d))) walk(join(jvmHome, d), `${d}/`);
}
console.log(`${compared} files compared, ${mismatches} mismatches${mismatches ? ` (see ${join(work, 'diff')})` : ''}`);
if (mismatches) process.exitCode = 1;
console.log(process.exitCode ? 'FAILED' : 'OK: browser build matches the JVM build');
