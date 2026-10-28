# Proposal

## Why

A pre-release code review of this release found five small defects in new or changed code. Each breaks behaviour the existing specs already require, or a project rule, so no requirement changes. Tracked by the linked tracker entry.

## What Changes

- **A noted Regenerate keeps the current version intact.** With a note, Direct API Regenerate sends the artifact's current text inside a code fence. That fence was three backticks, so the first code block in the artifact closed it early, and the model saw a mangled current version. The fence is now longer than any run of backticks in the text.
- **Direct API writes don't wait for a dialog just to label Local History.** Before each write, the new Local History step jumped to the UI thread to save unsaved edits, even when there were none. From Generate All Remaining, that jump waited for any open modal dialog, such as Settings, to close. It now jumps only when a file about to be written has unsaved edits. Generate All writes new files, so it no longer waits. The write itself still waits as it did before this release (tracked separately).
- **Right-clicking a DONE chip doesn't refresh the file system on the UI thread.** The "Show Local History" lookup, and the "Compare with previous" link, now find the file without a synchronous refresh.
- **Manage AI Tools → Update can't be started twice.** On the preview path, the Update button now shows the same busy state as the other buttons. It stays busy until the preview finishes: applied, cancelled, failed or found nothing to do. A double-click no longer opens two previews. The preview service gains a "finished" callback, so the button is restored on every outcome.
- **Every project service is registered in `plugin.xml`,** as the project's rule for services requires. `UpdatePreviewService` was new and missing, and two older services were missing too. A test now checks every one.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. Each fix restores behaviour the existing specs already describe, so the change declares `skip_specs`.

## Impact

- **Code:**
  - `ArtifactInstruction` (the fence);
  - `ArtifactLocalHistory` (saving only when needed);
  - `WorkflowActionPanel` (the find without refresh);
  - `ManageAiToolsDialog` and `UpdatePreviewService` (busy state and the finished callback);
  - `plugin.xml` (the registration).
- **Tests:**
  - the fence wraps text containing three- and four-backtick runs intact;
  - no UI-thread hop when nothing needs saving;
  - the chip menu doesn't refresh;
  - Update's busy state on the preview path;
  - every annotated project service is registered.
- **Docs:** none. Each defect is in a feature not yet released, so the CHANGELOG's entries for those features already describe the fixed behaviour.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported. No new platform APIs.
