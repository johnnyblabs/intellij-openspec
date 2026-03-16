## Why

Archiving changes one at a time is tedious when multiple changes are ready simultaneously. The single-change archive action also has no way to detect when two changes modify the same spec domain, which can cause silent overwrites during spec sync. A Bulk Archive action lets users archive multiple changes in one operation with conflict awareness.

## What Changes

- New `BulkArchiveDialog` showing a checkbox list of active changes with per-change completion status and conflict indicators
- Conflict detection logic that identifies when multiple selected changes have delta specs targeting the same capability
- Sequential archive with spec sync for each change, applying in user-selected order
- New `OpenSpecBulkArchiveAction` wired to the menu and toolbar

## Capabilities

### New Capabilities
- `bulk-archive`: Multi-change archive with conflict detection and sequential spec sync

### Modified Capabilities
- `workflow`: Adds Bulk Archive entry point to the workflow action panel

## Impact

- **Code**: New dialog, action, and conflict detection logic; `WorkflowActionPanel` gains Bulk Archive button; `plugin.xml` updated
- **Dependencies**: Reuses existing `ChangeService.archiveChange()` and `SpecSyncService`; no new external dependencies
