# Feedback action + support-doc accuracy fixes

## Why

The 1.4.x release-scoping audit (grounded in `docs/cli-versions/1.4.md` and an empirical sweep of the installed 1.4.1 CLI against the codebase) left two loose ends. First, `openspec feedback` is the one durable, IDE-relevant CLI command with no plugin surface at all — the support matrix itself marks it "no surface yet". Second, the audit found three accuracy defects in the plugin's own support documentation: `docs/openspec-support.md` classifies archive as *delegated* when the code path is a built-in VFS move, claims a 1.4 context-store *register* action that only exists for the 1.5 `store` model, and hedges `openspec set` as "not yet confirmed upstream" when `set change` is empirically present on 1.4.1. The matrix is the project's single source of truth for version facts; wrong cells defeat its purpose.

## What Changes

- **Send OpenSpec Feedback action.** A lightweight action (Tools menu / tool-window overflow) that collects a message (and optional body) and delegates to `openspec feedback <message> [--body]`, reporting success/failure through the standard notification surface. Hidden when the CLI is unavailable.
- **Support-matrix corrections** in `docs/openspec-support.md`: archive-change reclassified from `delegated` to `built-in`; the 1.4 coordination row corrected to `setup`-only (no `register` on the 1.4 line in the plugin); the `set` caveat replaced with the verified 1.4.1 fact (`set change --initiative/--store/--store-path/--json`).
- **Feature-delta doc closure** in `docs/cli-versions/1.4.md`: §5 open questions resolved with the empirically verified 1.4.1 command surfaces (`set` confirmed; full `context-store`/`initiative`/`workspace` subcommand lists); the delta table's `feedback` row updated when the action ships.
- **Spec strengthening:** the coverage-matrix requirement gains an accuracy clause — support-mechanism classifications (delegated vs built-in vs indirect) SHALL match actual code behavior, so the archive-style drift becomes a spec violation rather than an editorial slip.

## Capabilities

### New Capabilities

- `feedback-action`: user-initiated feedback submission to OpenSpec maintainers via the CLI.

### Modified Capabilities

- `plugin-documentation`: the coverage matrix requirement additionally mandates that support-mechanism classifications reflect verified code behavior.

## Impact

- **Code:** one new action class + registration in `plugin.xml`; delegation via the existing CLI runner off the EDT. No parsing of CLI output beyond exit status and stderr (no new contract fixtures needed; `feedback` has no `--json`).
- **Docs:** `docs/openspec-support.md`, `docs/cli-versions/1.4.md`, `docs/feature-reference.md`, CHANGELOG.
- **Tests:** action enablement + delegation tests (argument construction, failure notification path).
- **Compatibility:** no platform-API surface; IntelliJ 2024.2+ unaffected. `feedback` exists on the supported CLI range; the action degrades to hidden without a CLI.
