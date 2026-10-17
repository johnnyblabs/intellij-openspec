## Context

Archiving today is all plugin-side:
- **`ChangeService.archiveChange`** moves the folder in a `WriteAction`. Callers invoke it on the UI thread (the panel's `invokeLater`, the Archive and Verify actions).
- **Spec updates** come from the plugin's `SpecMerge` (via Sync Specs, or Bulk Archive's `sync` step), which now matches OpenSpec 1.14.3's archive.

What `openspec archive` offers, confirmed on 1.14.3 and in the captured `fixtures/cli/archive-1.14.3/`:
- **Flags:** `--yes` (skip prompts), `--skip-specs`, `--no-validate`, `--json`, `--store`.
- **Behaviour:** it validates the change, merges every capability all-or-nothing, and moves the change to `changes/archive/YYYY-MM-DD-<name>`.
- **Success JSON:** `{archive: {change, archivedAs, path, specsUpdated, totals, warnings?}, root}`.
- **Refusal:** exit 1, with `{archive: null, status: [{severity, code, message, fix}]}`.
- **Version notes:** `archive --json` exists from 1.5 and exits 1 on refusal (captured: `archive-1.5.0/`, where human mode still exits 0). 1.4.x rejects the flag (`error: unknown option '--json'`, exit 1, captured in `archive-1.4.1/`), and 1.3 exits 0 on refusal.

## Goals / Non-Goals

**Goals:** a CLI user's archive is their CLI's archive, with its validation, rules and messages; the plugin's prompts and gates still come first; no CLI process ever runs on the UI thread.

**Non-Goals:**
- `--no-validate`.
- Store roots.
- Replacing Sync Specs (OpenSpec has no sync command).
- Changing the archive gating in the panel (tracked separately).

## Decisions

### D1. One entry point, two strategies

`ChangeService.archiveChange(Change, boolean syncSpecs)` returns an `ArchiveOutcome(String archivedAs, boolean specsUpdated, List<String> warnings)` and throws `ArchiveRefusedException(message, fix)` on refusal.
- **When to use the CLI:** `CliDetectionService` reports a CLI at version 1.5 or later (`CliVersion.atLeast(version, "1.5.0")`). It then runs `CliRunner.run(project, "archive", name, "--yes", "--json")`, with `--skip-specs` when `!syncSpecs`.
- **Otherwise,** the existing plugin path runs: with `syncSpecs`, `SpecSyncService.computeSync` and `applySync` (refusals become an `ArchiveRefusedException`), then the dated-folder move.

The method must not be called on the UI thread; it asserts this. After a CLI run, it refreshes `openspec/` in the VFS so the tree and editors see the move and the spec edits.

### D2. Parse the CLI's JSON with a captured contract

`ArchiveJson.parse(stdout, exitCode)` maps the success and refusal shapes, contract-tested against the captured runs:
- 1.14.3: the existing `archive-1.14.3/` cases.
- 1.5.0: new captures of a success and a refusal at the floor.

A non-zero exit with unparseable output becomes a refusal carrying the raw stderr, so it's never silent. Warnings on success (for example "treating it as already removed") go into the success notification.

### D3. Callers move the archive off the UI thread

- **The panel's Archive and the Archive/Verify actions:** after their dialogs, they start a `Task.Backgroundable` that calls `archiveChange`, then report through `invokeLater` (popover, refresh, notification, or the error with the button re-enabled).
- **"Archive Without Syncing"** passes `syncSpecs=false`; a change without delta specs passes `true`, which is harmless either way.
- **Bulk Archive's `Archiver.forProject`:**
  - `sync` becomes a no-op on the CLI path;
  - `archive` calls `archiveChange(change, true)`, so the CLI merges and moves in one call;
  - `check` keeps the plugin's preview and conflict check, giving Blocked rows before anything runs;
  - a CLI refusal at run time marks the row Failed with the CLI's message.

  Bulk Archive's `archive` step currently runs on the UI thread with the dialog's modality; it moves to the run loop's background thread.

### D4. Fallback stays exactly as shipped

No CLI, or 1.3/1.4: the plugin's merge and move, unchanged from the previous change. The version check goes through a package-private seam, so tests can force either strategy without a real CLI.

## Risks / Trade-offs

- **The CLI may refuse what the preview allowed,** for example on validation the plugin doesn't run. → The refusal shows the CLI's message and fix, and the change stays active, which is the honest answer. Bulk Archive's Blocked check is a best-effort preview.
- **A CLI process per change in Bulk Archive is slower** than the in-process merge. → It's acceptable for an explicit batch action, and the rows show progress.
- **A CLI older than its own floor or broken output.** → Unparseable output with a non-zero exit is a refusal with stderr, and exit 0 with unparseable output is reported as an error after a VFS refresh, so the user can check.

## Migration Plan

Behaviour changes only for users with CLI 1.5 or later; others keep the shipped plugin archive. To roll back, revert the commit.
