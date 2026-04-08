## Why

Sync Specs is buried in the overflow menu and has no relationship to Archive in the UI. A user can archive a change with unsynced delta specs — silently discarding spec changes. The icon bar should enforce the natural order of operations (verify → sync → archive) and make Sync Specs a first-class action.

## What Changes

- Promote Sync Specs from the overflow menu to a dedicated icon button in the icon action bar, positioned immediately left of Archive
- Add an archive guard: when unsynced delta specs exist, Archive SHALL show a confirmation dialog offering to sync first, skip, or cancel
- Add contextual tooltip and enabled-state logic for the new Sync Specs icon button (enabled only when `hasDeltaSpecs` is true)
- Remove Sync Specs from the overflow menu (it moves to the icon bar)

## Capabilities

### New Capabilities

_None_ — all changes are modifications to existing capabilities.

### Modified Capabilities

- `pipeline-interaction`: Icon action bar gains a Sync Specs button; overflow menu loses Sync Specs
- `workflow`: Archive action gains a pre-archive guard that checks for unsynced delta specs

## Impact

- `WorkflowActionPanel.java` — icon bar construction, overflow menu, archive action logic
- `pipeline-interaction` spec — icon bar contents, overflow menu structure
- `workflow` spec — archive action behavior