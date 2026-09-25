# Walnut version 8.0-alpha
Walnut is an automated theorem prover for automatic words.

For full documentation, see the [Walnut wiki](https://github.com/Walnut-Theorem-Prover/Walnut/wiki).

For a list of changes since previous versions, see [the CHANGELOG](CHANGELOG.md).

<ins>**Older Walnut versions**</ins> Previous versions are listed [here](https://github.com/Walnut-Theorem-Prover/Walnut/tags)

<br>

To run Walnut, first run `build.sh` to build Walnut, then run the `walnut.sh` file. To see command-line arguments, run `walnut.sh --help`.

To run Walnut tests, run `build.sh` with the `-t` flag.

# Walnut in the browser

Walnut also runs as a static web app with nothing to install: the JVM core is compiled to JavaScript with [TeaVM](https://teavm.org) and runs in a Web Worker. Files you create are kept in the browser's storage, and `.gv` results are rendered in the page. Computations are limited by the memory a browser tab is allowed (a few GB at most), and a computation that exceeds it crashes the tab, so very large proofs still need the desktop version.

- `web/scripts/build-site.sh` builds the site into `web/dist` (needs a JDK and Node). `--wasm` compiles the core to WebAssembly GC instead; it builds, but the page does not load that variant yet.
- `node web/scripts/diff-jvm-web.mjs` runs the integration corpus through both builds and checks that every generated automaton is identical.
- `.github/workflows/pages.yml` deploys `web/dist` to GitHub Pages on every push to `main`.

The sources live in `web/`: `web/app` is the TeaVM entry point, the bridge to browser storage, and two browser substitutes (declared in `WalnutSubstitutionPolicy`) for classes that need threads or classpath scanning; `web/site` is the page. The CCL and CCLS determinization strategies are not available in the browser, since the OTF library behind them uses thread pools; every other command works. `build.sh -t` also compiles the browser version, so core changes that break it are caught locally.

# Help documentation

Walnut now provides built-in documentation for all commands using the `help` command. To view all commands for which documentation exists:
```
help;
```

To view documentation for a specific command:
```
help <command>;
```
