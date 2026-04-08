## Context

The plugin runs CLI commands via `CliRunner` and `CliDetectionService`, both of which used IntelliJ's `OSProcessHandler` to manage external processes. `OSProcessHandler.waitFor()` asserts that it is not called under a ReadAction — but action update cycles (`AnAction.update()`) run under ReadAction, creating an unavoidable conflict whenever workflow-enabled actions trigger CLI resolution.

Separately, `SpecSyncService.applyProjection()` wrapped plain `Files.writeString()` inside `WriteAction`, which is unnecessary for filesystem I/O and adds unneeded EDT contention. `WorkflowActionPanel` reads UI state on pooled threads (Swing threading violation) and shares mutable fields across threads without visibility guarantees.

## Goals / Non-Goals

**Goals:**
- Eliminate the `OSProcessHandler` ReadAction threading violation in all CLI execution paths
- Ensure correct Swing EDT discipline for UI state access in `WorkflowActionPanel`
- Ensure cross-thread field visibility with `volatile` where needed
- Separate filesystem I/O from VFS operations in `SpecSyncService`

**Non-Goals:**
- Making CLI execution fully asynchronous (callers still block on the result)
- Changing the CLI command interface or adding new commands
- Refactoring the action update cycle to avoid ReadAction entirely
- Adding coroutine-based or `ReadAction.nonBlocking()` patterns

## Decisions

### Use `Process` directly instead of `OSProcessHandler`

**Choice:** Replace `OSProcessHandler` with `GeneralCommandLine.createProcess()` + `Process.waitFor()`.

**Alternatives considered:**
- `ReadAction.nonBlocking()` wrapper — would require restructuring all callers into async chains; disproportionate effort for a correctness fix
- `CapturingProcessHandler` — still extends `OSProcessHandler` and inherits the same ReadAction check
- `ProcessBuilder` directly — loses `GeneralCommandLine`'s charset handling and environment setup

**Rationale:** `Process` has no ReadAction check, preserves the synchronous call contract, and `GeneralCommandLine` still handles command construction and charset. Minimal diff, maximum compatibility.

### Drain streams concurrently in `CliRunner`

**Choice:** Use `CompletableFuture.supplyAsync()` to drain stdout and stderr in parallel.

**Rationale:** `Process` requires the caller to drain streams to avoid buffer deadlock. Reading stdout then stderr sequentially could deadlock if stderr fills its buffer first. Concurrent draining with `CompletableFuture` is the standard pattern.

### Read-then-wait in `CliDetectionService.runAndCapture()`

**Choice:** Read `stdin.readAllBytes()` before calling `process.waitFor()`.

**Rationale:** Detection commands produce small output (version strings, paths). Reading all bytes first is simpler than concurrent draining and safe for small payloads. The `readAllBytes()` call blocks until EOF (process exit), then `waitFor()` confirms the exit code.

### Separate filesystem write from WriteAction in SpecSyncService

**Choice:** Call `Files.writeString()` on the background thread, then `invokeAndWait` with `WriteAction` only for VFS refresh.

**Rationale:** `Files.writeString()` is a plain Java I/O call that doesn't touch IntelliJ's VFS. Only the `LocalFileSystem.refreshAndFindFileByPath()` call needs VFS write-lock semantics. Separating them avoids holding WriteAction during disk I/O.

### Add `volatile` and capture UI state before dispatch

**Choice:** Mark five `WorkflowActionPanel` fields as `volatile`; capture `getSelectedDeliveryMode()` and `getSelectedToolName()` on EDT before `executeOnPooledThread`.

**Rationale:** These fields are written on pooled threads and read on EDT (or vice versa). Without `volatile`, the JMM permits stale reads. Capturing Swing component state on EDT before dispatch prevents `NullPointerException` or wrong-thread access.

## Risks / Trade-offs

- **[Risk] `Process` stream deadlock on large output** → Mitigated in `CliRunner` by concurrent `CompletableFuture` draining. `CliDetectionService` uses read-before-wait, which is safe because detection output is always small (< 1 KB).
- **[Risk] `Process` doesn't notify ProcessListeners** → No code in the plugin uses ProcessListeners from `CliRunner` or `CliDetectionService` outside of the removed handler callbacks. No impact.
- **[Risk] `volatile` is insufficient for compound operations** → The fields are independently read/written, not part of compound check-then-act sequences. `volatile` provides the necessary visibility guarantee.