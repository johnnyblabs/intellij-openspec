## Why

OpenSpec CLI **1.13.x** (1.13.0–1.13.2) has shipped, and the plugin should declare the line supported. Unlike the 1.9–1.12 lines, 1.13 is not a pure capture-and-declare.

- **1.13.0 made the CLI's delta parser laxer.** `RENAMED` and `REMOVED` entries written with any CommonMark bullet (`-`, `*`, `+`) now parse. The plugin's built-in validator still only accepts `-`. It therefore reports an ERROR on input the CLI accepts, which breaks the never-more-restrictive-than-the-CLI guarantee.
- **1.13.1 widened how the CLI counts tasks.** The plugin's local task counting diverges from the client's numbers. On a captured control, the CLI reports 1/6 tasks complete while the plugin shows 1/1.
- **1.13.1 added a new error response.** The plugin's validate-output parser would read it as a pass.

1.14.x support follows directly in the next release. This change is deliberately kept to the 1.13 line.

Tracked in the linked tracker entry.

## What Changes

- **Declare `1.13.x` a supported generation.** The floor stays `1.3.0`, with no ceiling. Bump the plugin's target version from `1.12.0` to `1.13.2`.
- **Built-in validator and IDE inspection accept any CommonMark bullet in `RENAMED` pairs** (`-`, `*`, `+`, or none), matching CLI 1.13.0. This fixes the one place the fallback is stricter than the CLI. Bullet-form `REMOVED` already passes the fallback, so its parity holds.
- **Spec sync parses what the client applies.** Two cases are covered:
  - Bullet-form `REMOVED` entries (``* `### Requirement: Name` ``) become REMOVED operations. They are silently skipped today.
  - `RENAMED` entries written in the client's canonical form (``- FROM: `### Requirement: Old` ``, any bullet) are normalized to bare requirement names before matching. Today the backticks and `### Requirement:` prefix are left in the name, so the rename never finds its target.

  The bare-name `FROM: Old name` form the plugin already accepts keeps working.
- **Task counting matches the client's task-line grammar.** Any bullet (`-`, `*`, `+`) or ordered marker (`1.`, `1)`) is accepted, the box may be empty or padded (`[]`, `[ x ]`), and a task is done only when its mark is `x`/`X`. This applies everywhere the plugin counts tasks: tree-node `X/Y`, workflow panel, Apply, and Verify. In-progress `[~]` stays not-done, as today.
- **An item-less CLI error response is a failed run, not a pass.** A `validate --json` response with no `items` that reports error-severity `status` entries (CLI 1.13.1's `invalid_item`) SHALL fall back to the built-in validator instead of reporting PASS.
- **Capture real 1.13.2 contract fixtures.** These are the parity-corpus twins (default and `--strict`), `version.txt`, and a positive control per fix: bullet `RENAMED`, bullet `REMOVED`, task markers, and the `invalid_item` response.
- **Not adopted:** 1.13.1's new verdict-affecting ERRORs. These are an unpaired `RENAMED FROM`, an unread delta file beside `spec.md`, an empty scenario body in a delta, and a `### Requirement: X ###` duplicate. Each leaves the fallback **laxer** than the CLI, which the support contract permits. Adopting them would make the fallback stricter than the 1.3–1.12 CLIs it also serves.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `plugin-core`: extend "Supported CLI versions and capability preservation". Add `1.13.x` to the declared version list, plus a "The 1.13.x line is a supported generation" scenario.
- `validation`: in "Delta spec validation", the RENAMED pair grammar accepts any CommonMark bullet. Add a new requirement under which an item-less CLI error response is a failed run that falls back to the built-in validator.
- `spec-sync`: in "Delta spec parsing", bullet-form REMOVED entries are parsed, and RENAMED names are normalized from the client's canonical form.
- `verify-workflow`: in "Pre-archive verification", task checkboxes are recognized with the client's task-line grammar.

## Impact

- **Code (`src/main`):**
  - `BuiltInValidator`, `DeltaSpecInspection` and `SpecSyncService`: RENAMED pattern.
  - `SpecSyncService`: bullet-form REMOVED parsing and RENAMED name normalization.
  - A shared task-line parser used by `ApplyPromptBuilder.countTasks` (and its tree, workflow-panel and Apply callers) and by `VerificationService`.
  - `CliOutputParser.parseJsonOutput`: error-response handling.
- **Tests/fixtures:**
  - New `src/test/resources/fixtures/cli/1.13.2/` holding the parity twins, `version.txt` and the positive controls.
  - Version guards extended: `ValidatorVerdictVersionStabilityTest.FLOOR`, `CliVersionAtLeastTest`, `CliVersionTest`.
  - `openspecTargetVersion` raised to `1.13.2` in `gradle.properties`, enforced by `TargetVersionSingleSourceTest`.
- **Docs (vendor-neutral, public mirror):** `openspec-support.md`, README, CHANGELOG, fixtures README.
- **Platforms/CLI:** no change to IntelliJ 2024.2+ compatibility. The floor stays at 1.3.0, with no ceiling.
