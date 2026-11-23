# Capture OpenSpec CLI 2.x contract fixtures

## Why

OpenSpec CLI 2.0.0, 2.1.0 and 2.1.1 changed where skills live, how tools are recorded and how `init` scopes an install. The plugin's contract tests only cover 1.x captures, and they read a single version (the target, 1.17.0). Every 2.x support change needs real 2.x output to build against. The plugin must also keep working with every CLI from 1.3.0 up, which can only be proven against captures from each supported line. This change captures that evidence first, before any 2.x behaviour changes. It is tracked in the linked tracker entry for CLI 2.x fixtures.

## What Changes

- **Capture three 2.x generations** into `src/test/resources/fixtures/cli/2.0.0/`, `2.1.0/` and `2.1.1/`, using the existing capture scripts extended for 2.x:
  - the AI-tool registry (`ai-tools.json`) and `init --help`;
  - detection cases (`detect/<case>/`), including `--scope project`, an unscoped 2.1 init (recorded on the home side), `own-files` lines, and a project mixing a 1.17 layout with a 2.x one;
  - workflow ids and invocations (`workflows.json`, `workflow-invocations.json`), recording `/openspec-<skill>` and Codex's `$openspec-<skill>`;
  - `update` transcripts and `update --dry-run --json` results, including the 2.x note codes;
  - `config list` output, `--version`, and the `.openspec-target` file each version writes;
  - the artifact instructions (`instructions design` and `instructions specs`) the generation prompts are built from;
  - init errors: `amazon-q` on 2.x, and `--scope` on 1.17.0 and 2.0.0.
  2.1.0 is captured separately from 2.1.1 because it installs skills over the network and lacks 2.1.1's tool inference and new warnings.
- **A `--help` sweep.** Capture `<command> --help` for every command the plugin runs, at the newest patch of every 1.x minor from 1.3 and at each 2.x version, into `<version>/help/<command>.txt`. A later change uses these to check every flag the plugin passes against the CLI it is talking to.
- **Links are recorded as data.** 2.x creates directory symlinks from tool folders into `.agents/skills/`. Captures record each link in a `links` list in `layout.json` (read with `lstat`, never followed). A test helper recreates them as a real symlink, a copied folder, or the plain-text file that git creates on Windows when symlinks are off. No real symlink is committed, and a hygiene test enforces that.
- **Contract tests run per fixture version.** The AI-tool registry, detection, workflow invocation and update-plan contract tests run over each captured version instead of a single hard-coded one. Where the plugin does not yet handle a 2.x version, the test records the gap in one named list and points to the change that closes it, so the gap is visible and cannot grow unnoticed.
- **Sanitize home-side state.** `projects.json` and any absolute HOME path are rewritten to the `/fixture/...` convention.
- **The verdict-stability guard** gains the 2.0.0, 2.1.0 and 2.1.1 validation corpora.
- **The fixtures README** gets a section per 2.x version (CLI `--version` and package tarball checksum) and documents the links schema.

No plugin behaviour changes. The supported CLI range and the target version are unchanged here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ci`: adds a requirement that contract fixtures record filesystem links as data and never commit real symlinks, so every platform's checkout sees the same fixture tree.

## Impact

- New fixtures under `src/test/resources/fixtures/cli/2.0.0/`, `2.1.0/`, `2.1.1/`, and `help/` folders under each captured 1.x minor.
- Capture scripts in `scripts/` gain 2.x cases, link recording, the help sweep and HOME-side sanitizing. Captures need network access to download each package, and 2.1.0's skill install needs Git and network.
- Test code only: a link materializer, a fixture hygiene test, and version-parameterized contract tests. No `src/main` change, so coverage floors are not ratcheted.
- No effect on IntelliJ Platform compatibility; 2024.2 and later stay supported.
- Unblocks the CLI version-compatibility matrix and the 2.x support changes that follow it.
