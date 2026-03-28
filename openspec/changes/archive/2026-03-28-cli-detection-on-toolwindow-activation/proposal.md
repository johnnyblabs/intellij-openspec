## Why

CLI detection only runs once at project startup. If the user installs the OpenSpec CLI after opening the project, the status bar stays red ("CLI: not found") until they manually click Refresh. This makes the plugin feel broken when the CLI is perfectly available.

## What Changes

- Re-run CLI detection when the OpenSpec tool window is activated (gains focus)
- Throttle re-detection so it doesn't spawn processes on every focus event — skip if last detection was within the last 30 seconds
- Update the status bar immediately after re-detection completes

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `plugin-core`: CLI detection lifecycle changes from once-at-startup to also-on-tool-window-activation with throttling

## Impact

- `OpenSpecToolWindowFactory.java` — add tool window activation listener
- `CliDetectionService.java` — add throttled `detectIfStale()` method with timestamp tracking
- `OpenSpecToolWindowPanel.java` — expose `updateCliStatus()` for external callers (or trigger via existing `refresh()`)