## Context

The IntelliJ Platform enforces strict threading rules: UI (Swing) operations must run on the EDT, long-running I/O must run on background threads, and `WriteAction` must execute on the EDT. Six call sites violate these rules — two use `invokeAndWait` from background tasks (deadlock risk), two perform I/O directly on the EDT (UI freeze), and two misplace VFS operations across thread boundaries.

All six violations are in the action/service layer. The underlying services (`ChangeService.archiveChange`, `ScaffoldingService.initOpenSpec`, `ScaffoldingService.createChange`) already use `WriteAction` internally, so they **require** EDT execution. The fix is not to change the services but to restructure how callers dispatch to them.

## Goals / Non-Goals

**Goals:**
- Eliminate all 6 identified EDT threading violations
- Preserve existing behavior — archive, init, propose, sync, and explore must produce identical outcomes
- Keep UI responsive during all file/process I/O operations
- Maintain correct `WriteAction` usage for VFS mutations

**Non-Goals:**
- Refactoring `ChangeService` or `ScaffoldingService` internals — they are correct as-is
- Adding coroutine-based threading — stay with platform `invokeLater`/`executeOnPooledThread` patterns already used elsewhere in the codebase
- Converting synchronous service APIs to async — only callers change

## Decisions

### D1: Replace `invokeAndWait` with `invokeLater` + callback chain

**Choice**: Convert `invokeAndWait` calls to `invokeLater` with success/error callbacks chained inside the lambda.

**Why**: `invokeAndWait` blocks the calling thread until the EDT finishes. If the EDT is busy (modal dialog, another `invokeAndWait`), this deadlocks. `invokeLater` posts the work without blocking.

**Alternative considered**: `WriteCommandAction.runWriteCommandAction` from background thread — rejected because `ChangeService.archiveChange` already wraps its own `WriteAction`, and nested write actions are fine but the outer call still needs EDT.

**Impact on callers**: Post-archive logic (popover, refresh) moves inside the `invokeLater` lambda instead of running sequentially after `invokeAndWait` returns.

### D2: `WorkflowActionPanel` archive — single `invokeLater` block

**Current**: Background task → `invokeAndWait { archiveChange }` → `invokeLater { showPopover + refresh }`.

**New**: Background task → `invokeLater { archiveChange; showPopover; refresh }`. The archive, popover, and refresh all run in one EDT dispatch since `archiveChange` is fast (just a VFS move). Error handling wraps the entire block with `try/catch` inside the lambda.

### D3: `BulkArchiveDialog` — sequential `invokeLater` per iteration with completion tracking

**Current**: Background loop → `invokeAndWait { archiveChange }` per iteration.

**New**: Background loop collects work items, then posts each archive as `invokeLater`. Each lambda updates the table model and checks if it's the last item to fire the completion notification. A `CountDownLatch` or `AtomicInteger` tracks remaining items so the background task can wait for all archives to finish before showing the summary.

**Alternative considered**: Single `invokeLater` that loops through all archives — rejected because it blocks EDT for the entire batch. Per-item dispatch lets EDT breathe between archives.

### D4: `OpenSpecInitAction` — wrap in `ProgressManager.Backgroundable`

**Current**: `actionPerformed` calls `scaffolding.initOpenSpec()` directly on EDT, which may invoke `CliRunner.run()` (process I/O).

**New**: `actionPerformed` launches a `Task.Backgroundable`. Inside the background task, call `initOpenSpec()` via `invokeAndWait` (safe here — no modal conflict since we control the context) or restructure `initOpenSpec` to separate I/O from WriteAction. The notification and tool window refresh happen via `invokeLater` on completion.

**Chosen approach**: Use `invokeLater` from the background task to run `initOpenSpec()` on EDT (it needs WriteAction), then chain notification + refresh. The CLI detection and process execution happen before the `invokeLater` call.

**Revised approach**: Since `ScaffoldingService.initOpenSpec` internally calls `WriteAction.compute` which needs EDT, but also conditionally calls `CliRunner.run` which is I/O — split the operation: run CLI detection + CLI execution on the background thread, then `invokeLater` for the VFS scaffold + refresh. If CLI is unavailable, the built-in scaffold (pure `WriteAction`) runs entirely via `invokeLater`.

### D5: `OpenSpecProposeAction` — `executeOnPooledThread` + `invokeLater`

**Current**: `actionPerformed` calls `scaffolding.createChange()` directly on EDT.

**New**: After dialog returns, dispatch to `executeOnPooledThread`. Inside the pooled thread, call `invokeLater` to run `createChange` (needs WriteAction/EDT), then chain notification, refresh, and autoFocusChange inside the same `invokeLater` block.

### D6: `SpecSyncService.applySync` — `invokeLater` for VFS refresh, synchronization gate

**Current**: Loop calls `invokeAndWait` per file for VFS refresh.

**New**: Replace `invokeAndWait` with `invokeLater`. Since `applySync` returns `postMergeWarnings` and callers need the result, we need the VFS refresh to complete before `validatePostMerge`. Use `CountDownLatch` per iteration: `invokeLater` decrements the latch, background thread waits on it. This avoids `invokeAndWait` while still synchronizing.

**Alternative**: Fire-and-forget `invokeLater` + batch VFS refresh at end — rejected because `validatePostMerge` reads the VFS state immediately after each file.

### D7: `ExploreContextAction.deliverEditorTab` — VFS refresh before EDT hop

**Current**: `executeOnPooledThread` → write file → `invokeLater { refreshAndFindFileByNioFile + openFile }`.

**New**: `executeOnPooledThread` → write file → `refreshAndFindFileByNioFile` (on pooled thread) → `invokeLater { openFile(vf) }`. VFS refresh is safe on background threads; only the editor open needs EDT.

## Risks / Trade-offs

- **Race condition in bulk archive**: Per-item `invokeLater` means archives interleave with other EDT work. Mitigated by disabling the OK button during archive and tracking completion with an atomic counter.
- **Error visibility**: `invokeLater` errors are swallowed unless explicitly caught inside the lambda. Every `invokeLater` block must have its own `try/catch` with `OpenSpecNotifier.error` reporting.
- **SpecSyncService latch pattern**: `CountDownLatch` + `invokeLater` is unusual. If the EDT is starved, the background thread blocks on the latch. This is strictly better than `invokeAndWait` (no deadlock from nested EDT calls) but could still stall under extreme EDT load. Acceptable for the small number of files in a typical sync.
- **Testing**: These are threading changes — unit tests can verify logic but not threading correctness. Manual testing under modal dialogs (e.g., open Settings while archiving) is essential.
