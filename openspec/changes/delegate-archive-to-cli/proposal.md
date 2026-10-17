## Why

When it archives a change, the plugin moves the folder and merges delta specs itself, even when the OpenSpec CLI is installed. The previous change made that merge match `openspec archive` 1.14.3, but a port can only match the version it was ported from. The CLI also validates the change before archiving, which the plugin's merge doesn't do. Users with the CLI should get their own CLI's archive: its rules, its validation and its messages.

The plugin also runs its archive move on the UI thread, which is fine for a folder move but not for a CLI process.

Tracked in the linked tracker entry.

## What Changes

- **Delegate the archive to the CLI.** With OpenSpec CLI 1.5 or later, every plugin archive runs `openspec archive <name> --yes --json`:
  - the panel and menu Archive;
  - "Archive Without Syncing", which adds `--skip-specs`;
  - Bulk Archive, where the CLI merges the specs and moves the change in one step.

  `--yes` only skips the CLI's prompts. The plugin keeps its own gates in front of it: the Verify dialog, the task and artifact checks, the unsynced-delta-specs prompt, and Bulk Archive's Blocked check.
- **Show the CLI's answer.** On success, the notification names the archived folder (`archivedAs`) and whether specs were updated. On a refusal (non-zero exit), the plugin shows the CLI's `status[].message` and `fix`, and the change stays active. Bulk Archive marks that row Failed with the CLI's message.
- **Keep the plugin's own archive as the fallback,** with no CLI or a CLI older than 1.5. 1.4 has no `archive --json`, and 1.3 exits 0 when it refuses, so neither can be relied on here. The fallback uses the merge and dated-folder rules from the previous change.
- **Run archives off the UI thread.** The archive runs in a background task, and UI updates follow on the UI thread.
- **Out of scope:** `--no-validate`, since the CLI's validation is part of what's being delegated; store-backed change roots (`--store`); and Sync Specs, which stays the plugin's preview-and-apply, because OpenSpec has no sync command.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `workflow`:
  - the Archive action runs the archive off the UI thread;
  - a new requirement says archives go to the CLI when version 1.5 or later is available;
  - the archive-target-directory requirement now applies only to the plugin's fallback archive.
- `bulk-archive`: each change is synced and archived in one CLI call when the CLI is available.

## Impact

- **Code:**
  - `ChangeService.archiveChange`: CLI path, the fallback, and a `syncSpecs` flag.
  - A small parser for the CLI's archive JSON.
  - `WorkflowActionPanel`, `OpenSpecArchiveAction` and `OpenSpecVerifyAction`: background dispatch and result notifications.
  - `BulkArchiveDialog.Archiver.forProject`: one CLI call per change.
- **Tests:**
  - contract tests on the captured `archive --json` output from 1.14.3 (the existing `archive-1.14.3/` fixtures) and from the 1.5.0 floor;
  - a test that the CLI path is chosen only on 1.5 or later;
  - a threading test that the archive never runs on the UI thread.
- **Docs:** CHANGELOG, and the `docs/feature-reference.md` Archive and Bulk Archive entries.
