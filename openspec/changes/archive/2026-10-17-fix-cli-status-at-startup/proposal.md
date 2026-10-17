## Why

When a project opens, the OpenSpec tool window can say **"CLI: not found (built-in mode)"** even though the CLI is installed. It stays wrong until the user clicks Refresh. It was seen in a sandbox pass with CLI 1.14.3 on PATH. The cause is a race between three pieces of code:

1. **Startup detection** (`OpenSpecProjectService.StartupDetection`) calls `CliDetectionService.detect()` when the project opens.
2. **`detect()` clears its state first** (`available = false`, no path, no version) and only then probes. Anything reading the state mid-detection is told the CLI is unavailable.
3. **The tool window draws its CLI status once, when it is built.** The later re-checks (on tool-window show and on focus regain) go through the 30-second-throttled `detectIfStale()`, and only redraw if they actually ran. Startup detection has just run, so they skip, and the wrong label stays.

Any other code reading detection during that window, such as validation choosing between the CLI and the built-in validator, is briefly told the CLI is absent too.

Tracked in the linked tracker entry.

## What Changes

- **No in-between state.** `detect()` works out the new result privately and publishes path, version and availability together when it finishes. Readers only ever see the previous result or the new one.
- **Detection announces when it finishes.** `CliDetectionService` publishes a project-level event after every completed detection, whether at startup, from the throttled re-checks, from Settings or from a manual refresh. The tool window's CLI status redraws on that event instead of relying on the throttled re-check.
- **A "detecting…" state.** Until the first detection completes, the tool window shows **"CLI: detecting…"** instead of "not found".
- **Tests:** a headless race test holds detection part-way, builds the panel during it, and checks the label goes from detecting to available once detection finishes, with no refresh. Unit tests check that readers never observe a transient "unavailable".

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `plugin-core`: a new requirement says the CLI status shows the latest completed detection, whichever path triggered it, never a transient mid-detection state, and shows a detecting state before the first result.

## Impact

- **Code:** `CliDetectionService` (atomic publish, a "detected at least once" flag, a completion event), `OpenSpecToolWindowPanel` (subscribe, detecting state), and possibly `OpenSpecToolWindowFactory` (its throttled re-check no longer needs to redraw).
- **Tests:** a headless race test of the panel label and `CliDetectionService` unit tests, both using the existing process-runner test seam.
- **Platforms:** no change to IntelliJ 2024.2+ compatibility. The project message bus and `Topic` are stable APIs.
