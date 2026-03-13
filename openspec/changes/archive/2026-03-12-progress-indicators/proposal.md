## Why

CLI commands and API calls appeared to hang with no visual feedback. Users had no way to know if a generation, validation, or archive operation was running or stuck.

## What Changes

- Wrap `executeGeneration()` in `Task.Backgroundable` with cancellable progress indicator
- Wrap `onValidateChange()` in `Task.Backgroundable` (non-cancellable)
- Wrap archive operation in `Task.Backgroundable` with `ProcessCanceledException` handling
- All three operations now show IntelliJ's native progress bar in the status bar

## Capabilities

### New Capabilities
_(none)_

### Modified Capabilities
- `workflow-panel`: Add progress indicators for generation, validation, and archive operations

## Impact

- **File modified**: `WorkflowActionPanel.java`
- **Risk**: Low — wrapping existing `executeOnPooledThread` calls in `Task.Backgroundable`
