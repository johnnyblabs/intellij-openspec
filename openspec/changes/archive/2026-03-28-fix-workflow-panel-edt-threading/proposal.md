## Why

`WorkflowActionPanel.setActiveChange()` calls `refreshForChangeOnPool()` directly without dispatching to a background thread. This method invokes `ArtifactOrchestrationService.getArtifactStatus()` which can call the OpenSpec CLI — a blocking I/O operation running on the EDT. This causes UI freezes when the user selects a change in the tree view, as the tree selection handler fires on the EDT and chains through to `setActiveChange()`.

The sibling method `refreshForChange()` correctly wraps the same call in `executeOnPooledThread()`, so this is an inconsistency — `setActiveChange()` should use the same pattern.

## What Changes

- **Fix `WorkflowActionPanel.setActiveChange()`** to call `refreshForChange()` (which already dispatches to a pooled thread) instead of calling `refreshForChangeOnPool()` directly on the EDT.
- **Add a test** verifying that `setActiveChange()` does not invoke CLI/orchestration on the calling thread.

## Capabilities

### New Capabilities
_None._

### Modified Capabilities
- `workflow`: The workflow action panel's `setActiveChange()` method moves its CLI-invoking work off the EDT.

## Impact

- **Code**: Single-line fix in `WorkflowActionPanel.setActiveChange()`.
- **Behavior**: Tree selection no longer blocks the UI while fetching artifact status.
- **Risk**: Minimal — `refreshForChange()` is already the established pattern used by `refresh()` internally.
