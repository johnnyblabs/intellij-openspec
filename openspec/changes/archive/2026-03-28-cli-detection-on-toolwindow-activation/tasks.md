## 1. Throttled detection in CliDetectionService

- [x] 1.1 Add `lastDetectionTime` field (`volatile Instant`) and `DETECTION_STALENESS` constant (30 seconds) to `CliDetectionService`
- [x] 1.2 Add `detectIfStale()` method that checks timestamp and calls `detect()` only if stale; update timestamp after detection
- [x] 1.3 Update existing `detect()` to also record the timestamp so startup detection counts toward throttling
- [x] 1.4 Add unit test for `detectIfStale()` throttling logic

## 2. Tool window activation listener

- [x] 2.1 Register a `ToolWindowManagerListener` in `OpenSpecToolWindowFactory.createToolWindowContent()` that listens for `toolWindowShown` events filtered to the "OpenSpec" tool window
- [x] 2.2 In the listener, call `detectIfStale()` on a pooled thread, then update the status bar on the EDT

## 3. Status bar update access

- [x] 3.1 Change `updateCliStatus()` in `OpenSpecToolWindowPanel` from private to package-private so the factory listener can call it
- [x] 3.2 Store the `OpenSpecToolWindowPanel` reference in the factory listener (or retrieve it from the tool window content)

## 4. Verification

- [x] 4.1 Run existing tests to confirm no regressions
- [x] 4.2 Run full test suite