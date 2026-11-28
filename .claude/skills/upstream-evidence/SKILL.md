---
name: upstream-evidence
description: Where the evidence about upstream OpenSpec lives in this repo and how to get more — any CLI release's installed package (dist source) without npx, the captured-fixture layout and its index, how to add a capture case, and how replaying tests are tagged. Use when a question turns on what a specific OpenSpec release does or emits, when checking a claim against captured CLI output, or when adding or re-capturing fixtures.
---

# Upstream evidence: CLI sources and captured runs

Facts about upstream come from its code and from captured real runs, never from memory or a hand-written shape.

## Any release's source (`dist/`)

`npm root -g` finds only the installed CLI. For any other release, use the shared capture cache. It installs a release on first use, under `$OSP_CAPTURE_CACHE` (by default `<tmp>/osp-capture-pkgs`):

```bash
python3 -c "import sys; sys.path.insert(0, 'scripts/lib'); import openspec_capture as c; print(c.package('2.2.0'))"
# -> <cache>/2.2.0/node_modules/@fission-ai/openspec ; read dist/ there
```

- **Never `npx @fission-ai/openspec@<v>`.** Its cache leaks into captured fixtures.
- **Running a release:** `c.cli(v)` gives the argv for it, and `c.run(argv, cwd, home)` runs it isolated: its own HOME and XDG directories, `OPENSPEC_TELEMETRY=0`, and a scratch npm cache.
- **Where to look in `dist/`:**
  - `core/archive.js` (archive, merge, retirement);
  - `core/change-metadata/schema.js` (`.openspec.yaml` fields);
  - `utils/change-metadata.js` (marker reading);
  - `core/templates/workflows/*.js` (the agent skills' text);
  - `cli/index.js` (commands and flags).
- **Cite evidence as `dist/<file>:<line>`** with the release.

## Captured runs

- **Layout:** fixtures live under `src/test/resources/fixtures/cli/`, normally `<version>/<area>/<case>/` (for example `2.2.0/retire/clean/`). Older areas sit at the top level (`archive-2.1.1/`, `coordination-*.json`).
- **The index is `src/test/resources/fixtures/cli/README.md`.** Each section names its capture script, releases, date, cases and what they show. Read it before claiming a fixture exists or what it proves.
- **Capture scripts** are `scripts/capture-*.py`, and all use `scripts/lib/openspec_capture.py`. Before a write, `assert_sanitized` refuses a fixture that still holds machine paths.
- **Adding a case:**
  1. Extend the script's case table.
  2. Re-run the script for every release listed in its docstring.
  3. Check that only the new case's files appear (`git status`).
  4. Record the case in the README section.

  Don't edit a fixture by hand.

## Tests that replay captures

- **Tag them:** a plain-JUnit test that reads `FixtureVersions` belongs to the `cliFixtureTest` suite and needs `@CliFixtureSuite`. `CliFixtureSuiteGuardTest` fails until it has the tag.
- **Run them:** `./gradlew cliFixtureTest --tests '<Class>'`.
- **Make them fail when they should:** a test over captured cases asserts the exact set of case names it expects per release, and a minimum case count, so a missing directory can't shrink it to a pass.
