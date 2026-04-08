## Why

The plugin uses IntelliJ's `OSProcessHandler` to run CLI commands and `WriteAction` blocks for file I/O. `OSProcessHandler.waitFor()` performs a ReadAction threading check that throws when called from action update cycles (which already run under ReadAction). Similarly, `WriteAction` was wrapping plain filesystem writes that don't require it, and cross-thread field access lacked volatile guarantees. These violations surface as `Throwable` logs in the IDE and risk UI freezes.

## What Changes

- Replace all `OSProcessHandler` usage with `Process` directly (`GeneralCommandLine.createProcess()`) in `CliRunner` and `CliDetectionService`, eliminating the ReadAction threading check
- Drain stdout/stderr concurrently via `CompletableFuture` in `CliRunner` to prevent buffer deadlock
- Extract a shared `runAndCapture()` helper in `CliDetectionService` to consolidate three separate OSProcessHandler-based methods
- Move `Files.writeString()` out of `WriteAction` in `SpecSyncService`, keeping only VFS refresh inside `WriteAction`
- Add `volatile` to fields in `WorkflowActionPanel` that are written on pooled threads and read on EDT
- Capture UI state on EDT before dispatching to background thread in `WorkflowActionPanel`

## Capabilities

### New Capabilities

_None — this is a correctness fix, not a new capability._

### Modified Capabilities

- `plugin-core`: CLI execution no longer uses `OSProcessHandler`, changing the process lifecycle and stream handling contract
- `spec-sync`: File write and VFS refresh are now separated across thread boundaries, changing the write-then-refresh contract

## Impact

- **CliRunner.java**: Complete rewrite of process execution — removes `OSProcessHandler`, `ProcessListener`, `ProcessOutputTypes` imports; adds `Process`, `CompletableFuture`, `InputStream`, `TimeUnit`
- **CliDetectionService.java**: Same OSProcessHandler removal across `tryPath()`, `tryLoginShellWhich()`, and `resolveLoginShellPath()`; new `runAndCapture()` private method
- **SpecSyncService.java**: `applyProjection()` method — file write moved outside `WriteAction`/`invokeAndWait` block
- **WorkflowActionPanel.java**: Five fields gain `volatile`; `handleApplyAction()` captures delivery mode and tool name on EDT before thread dispatch
- **No API or dependency changes** — all modifications are internal threading/lifecycle adjustments