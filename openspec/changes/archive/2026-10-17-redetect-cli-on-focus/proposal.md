## Why

During a long IDE session the plugin doesn't notice when the OpenSpec CLI is upgraded underneath it. The tool window's CLI status keeps showing the version detected when the project opened. In a real incident, a days-long session showed `CLI: available v1.6.0` while the machine had 1.7.0 installed.

Re-detection runs only on project open, when the OpenSpec tool window is *shown* (throttled to once per 30 seconds), and from explicit Settings or wizard actions. A tool window that stays docked and visible never fires "shown" again, so the stale version persists until the project is reopened.

Tracked in the linked tracker entry.

## What Changes

- **Re-detect the CLI when the IDE regains focus.** When the user returns to the IDE window of an OpenSpec project, the plugin re-runs CLI detection off the UI thread and refreshes the tool window's CLI status. This reuses exactly what already happens when the tool window is shown.
- **The existing 30-second staleness limit still applies,** so switching windows back and forth never launches repeated `openspec --version` runs.
- **Only the project whose window regained focus is refreshed.** Other open projects refresh when their own window is focused.
- **No change to the version-gated behaviour itself.** Features already read the detected version; they now see the fresh one sooner.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `plugin-core`: in "CLI re-detection on tool window activation", re-detection also runs when the IDE window of an OpenSpec project regains focus, with the same 30-second limit and the same status refresh.

## Impact

- **Code:** `OpenSpecToolWindowFactory`. The existing "tool window shown" re-detect-and-refresh block is extracted into one method, called both from that listener and from a new IDE-focus listener scoped to the tool window's lifetime. `CliDetectionService.detectIfStale()` is unchanged.
- **Tests:** the routing decision (refresh only for this project's frame) and detection picking up a changed CLI version through the service's injectable process runner.
- **Platforms:** uses `ApplicationActivationListener`, present and not deprecated on 2024.2. `verifyPlugin` confirms the range.
