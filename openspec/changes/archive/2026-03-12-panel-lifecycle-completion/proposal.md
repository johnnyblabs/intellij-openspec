## Why

The workflow panel guides users through Propose → Generate → Apply but drops them at the finish line — when all tasks complete, the panel shows "All complete" text with no actionable button. Users must navigate to the main menu or tree context menu to archive. This breaks the self-contained lifecycle the panel should provide. Additionally, there's no change-level validation — users can't verify their artifacts are well-formed without running project-wide validation from the toolbar.

## What Changes

- Add an "Archive & Sync" button to the workflow panel that appears when all tasks are complete
- Implement the archive operation inline (move to archive, sync delta specs, post-archive automation)
- Add a post-archive confirmation state showing what happened (specs synced, trackers updated) with a "Start New Change" button
- Add change-level validation that auto-runs when all artifacts become DONE (entering Build phase) and when all tasks complete (entering Ship phase)
- Surface validation results on pipeline chips (warning/error indicators) and in the guidance area

## Capabilities

### New Capabilities

### Modified Capabilities
- `workflow-panel`: Add archive button state, post-archive confirmation state, and change-level validation at phase transitions
- `validation`: Add per-change validation method to BuiltInValidator

## Impact

- `WorkflowActionPanel.java` — new archive button, post-archive UI state, validation integration at phase transitions
- `BuiltInValidator.java` — new `validateChange(String changeName)` method for single-change validation
- `OpenSpecArchiveAction.java` — extract reusable archive logic that the panel can call
- `ArchiveSyncService.java` — may need to expose sync as a callable method for panel use
