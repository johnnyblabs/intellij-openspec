## 1. Detection state

- [x] 1.1 Rework `CliDetectionService` per D1: an immutable snapshot published once at the end of `detect()` (and by a successful `tryPath`), with `isDetectedOnce()`. Verify: a unit test runs `detect()` with the process-runner seam held part-way, after a previous detection found the CLI, and asserts `isAvailable()` stays true and path and version stay consistent until the new snapshot is published. Mutation check: restoring the reset-first behaviour fails it. The existing `CliDetectionService` tests stay green.
- [x] 1.2 Add the D2 `detectionCompleted` topic, published after every snapshot (found or not), skipped when the project is disposed. Verify: a test subscribes and receives exactly one event per `detect()`, with the right availability and version.

## 2. Tool-window status

- [x] 2.1 `OpenSpecToolWindowPanel` subscribes to the topic (connection tied to its disposable) and redraws its CLI status; `updateCliStatus()` shows "CLI: detecting…" until `isDetectedOnce()` (D3). Remove the factory's now-redundant redraw. Verify: a headless race test builds the panel while detection is held mid-way, asserts the label reads "CLI: detecting…", releases detection, pumps events, and asserts "CLI: available v<version>" with no refresh call. Mutation checks: dropping the subscription, or the D1 atomic publish, makes it fail. (Done: dropping the subscription fails it, and so does dropping the "detecting…" state. The test first lets the panel's own initial refresh finish, which also redraws the status, so the announcement is the only remaining path, as in the real race. The D1 atomic-publish mutation can't affect a first detection, which has no earlier result to clear; it is caught by the 1.1 test instead.)

## 3. Docs and verify

- [x] 3.1 Add a CHANGELOG `### Fixed` entry (user-facing: the tool window no longer says the CLI isn't found while it's still being detected when a project opens). Update `docs/feature-reference.md` if it describes the CLI status line. Verify: the entry is present with no internal wording.
- [x] 3.2 Sandbox pass: open a project with the CLI on PATH and the OpenSpec tool window visible at startup; the status shows "CLI: detecting…" briefly, then "CLI: available v…", without clicking Refresh. Verify: observed and recorded here. **Sandbox result (2026-10-17, CLI 1.14.3):** after the project opened, the tool window's status line read "CLI: available v1.14.3" without clicking Refresh, and never showed "not found".
- [x] 3.3 Run `./gradlew build`, `./gradlew verifyPlugin` and `openspec validate fix-cli-status-at-startup --strict`. Verify: all pass, with no new deprecated usages.
