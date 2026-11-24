# Scripts

Utility scripts for development, testing, and marketplace publishing.

## For Contributors

These scripts work out of the box — no special infrastructure required:

| Script | Purpose |
|--------|---------|
| `setup-tokens.sh` | Interactive guide for configuring API tokens (Forgejo, Plane) |
| `setup-signing.sh` | Generate plugin signing keys for JetBrains Marketplace |
| `capture-screenshots.sh` | Capture marketplace screenshots at 1280x800 |
| `demo-project.sh` | Create a demo project for screenshots and testing |

## Contract-fixture capture

The contract tests read real CLI output captured by these scripts into `src/test/resources/fixtures/cli/<version>/`.
Each installs the release it captures into a scratch npm prefix and runs it under an isolated HOME, so your own
OpenSpec setup is never touched. `src/test/resources/fixtures/cli/README.md` records what each one produced.

| Script | Captures |
|--------|----------|
| `capture-ai-tools-fixtures.py` | The tool registry, `init --help`, and detection layouts (with links recorded as data) |
| `capture-workflow-ids.py`, `capture-workflow-invocations.py` | Workflow ids, and what to type to run each workflow per tool |
| `capture-explore-fixtures.py` | The files `init` writes per tool for the explore and update workflows |
| `capture-update-dry-run-fixtures.py` | `update --dry-run --json` plans |
| `capture-cli-generation.py` | Version, config list, init errors, known projects and the validation parity corpus, per release |
| `capture-help-sweep.py` | `--help` for every command the plugin runs, across every supported release |
| `install-cli-matrix.py` | Installs every swept release for the optional `./gradlew cliMatrix` gate (no capture) |

CLI 2.1 and later need Node 22.20 or newer; capturing 2.1.0 also needs Git and network access.

`./gradlew cliMatrix` is an optional local release gate. It runs every CLI command line the plugin can build against the real package of each release with a `help/` sweep, in an isolated HOME. It fails on an `unknown option` or `unknown command` error. Install the packages with `install-cli-matrix.py`, then run the gate with the same `OSP_CAPTURE_CACHE`. Without installed packages the gate is skipped. It isn't part of `./gradlew build`.

## Libraries

| File | Purpose |
|------|---------|
| `lib/common.sh` | Shared utilities (logging, environment loading) |
| `lib/openspec_capture.py` | Shared capture helpers: install a release, isolate HOME, sanitize paths, record links |

## Build & Test

You don't need any of these scripts to build or test the plugin. Standard Gradle commands work:

```bash
./gradlew build      # Compile + test
./gradlew test       # Tests only
./gradlew runIde     # Launch sandboxed IDE with plugin
```

Or use the Makefile:

```bash
make build           # Same as ./gradlew build
make test            # Same as ./gradlew test
make install         # Build and install to local IDE
```
