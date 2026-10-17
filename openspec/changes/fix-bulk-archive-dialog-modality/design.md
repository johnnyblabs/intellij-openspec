## Context

See proposal.md, Why. `BulkArchiveDialog` is a modal `DialogWrapper` opened by the Bulk Archive action. Its constructor starts `loadArtifactStatus()`, which computes statuses on a pooled thread and posts `fireTableDataChanged()` with a plain `invokeLater`. **OK** starts a `Task.Backgroundable` that, for each change:
1. runs `SpecSyncService.computeSync` / `applySync`;
2. archives it with `invokeLater(() -> changeService.archiveChange(...))` plus a `CountDownLatch`;
3. finally posts a last `invokeLater` that re-enables OK and notifies.

Findings from a platform analysis against the 2024.2.6 sources:
- **Status column:** from a pooled thread with no progress indicator, `defaultModalityState()` is NON_MODAL, so the status refresh is held until the dialog closes. `ModalityState.current()` doesn't help either, because the load starts in the constructor, before the dialog has entered modality.
- **Archive loop:** the `Backgroundable` is started on the EDT inside the dialog, so its `BackgroundableProcessIndicator` takes the dialog's modality, and plain `invokeLater` calls in `run()` inherit it. The loop therefore works in production, but only because of how it happens to be started.
- **Test mode:** `Task.isHeadless()` runs the task body synchronously on the EDT, so `invokeLater` plus `latch.await()` deadlocks.
- **Stuck dialog:** Escape and the window's close button both go through `doCancelAction()`, which does nothing while Cancel is disabled. OK is re-enabled only by the loop's last statement.
- **`archiveChange`:** it runs `WriteAction.run` with VFS `createChildDirectory`/`move`, so it needs the EDT on 2024.2.
- **`applySync`:** it uses the same `invokeLater` plus latch for a VFS refresh inside `WriteAction`. Its other two callers (the Sync action and the workflow panel) start their task only after their preview dialog has closed.

## Goals / Non-Goals

**Goals:** status visible while the dialog is open; no reliance on inherited modality; a dialog that can always be closed; an archive loop that real-code tests can exercise.

**Non-Goals:**
- Moving conflict detection (file reads on each checkbox toggle) off the EDT.
- Changing the archive semantics (order, spec sync, last-write-wins).
- Replacing the progress task with a modal progress dialog.

## Decisions

### D1. Capture the dialog's modality once, after `init()`

The constructor stores `dialogModality = ModalityState.stateForComponent(getContentPanel())` right after `init()`. The dialog window exists by then, and the platform caches the modality entity for that window, reusing the same object when the dialog is shown. So the captured state is the one that becomes current. Every UI post-back uses `invokeLater(r, dialogModality, ignored -> isDisposed())`, so nothing runs against a closed dialog.

*Alternatives considered:*
- `ModalityState.current()` in the constructor: still NON_MODAL there, because the dialog hasn't been shown yet.
- `ModalityState.any()`: the posted work touches more than pure UI state, and the platform discourages it.

### D2. Archive and spec-sync hand-offs use `invokeAndWait` with an explicit modality

- **The archive step** becomes `ApplicationManager.getApplication().invokeAndWait(() -> archive(row), dialogModality)`, replacing `invokeLater` plus a latch.
- **The refresh step in `applySync`** becomes `invokeAndWait(..., ModalityState.defaultModalityState())`. Called from a progress task, that is the task's own modality, which is the dialog's for Bulk Archive and non-modal for the other two callers. Called on the EDT, it runs inline.

Both hops stay on the EDT, because the write actions require it. `invokeAndWait` runs inline when already on the EDT, which removes the test-mode deadlock. The deadlock the old comment warned about came only from a wrong modality, and passing it explicitly removes that. The worker holds no read lock at these points, and the platform asserts that.

*Alternatives considered:*
- Keeping `invokeLater` plus a latch with the modality passed: correct in production, but still a deadlock in test mode, so the loop would stay untested.
- `Task.Modal`: a second modal progress window stacked on the dialog. That's worse for the user, and the write hop would still be needed.
- Running the archive entirely off the EDT: not allowed for these VFS writes on 2024.2.

### D3. Completion handling moves to `onFinished()`

Re-enabling OK and the summary notification move from the end of `run()` into the task's `onFinished()`. The platform calls it on the EDT with the task's modality, whether `run()` returned or threw. The success count is gathered from the rows.

### D4. A testable loop and status load

The per-change body becomes a package-private `archiveAll(List<ChangeRow>, ModalityState)`, called from `run()`. Status computation becomes a method that takes the modality to post with. Tests call `LaterInvocator.enterModal` on the EDT, capture `ModalityState.current()`, run the method from a pooled thread, and pump events. They assert the rows reach their final state before the modal is left. A negative control posts with `nonModal()` and checks that nothing arrives until the modal is left. The services involved (`ChangeService`, `SpecSyncService`, `ArtifactOrchestrationService`) are replaced with light test implementations, or driven against a temp project, as the test-engineer plan decides at the start of apply.

### D5. Guard against `invokeLater` without a modality in the dialog

`SettingsPanelModalityHygieneTest`'s balanced-parentheses scan is generalized into a small shared helper. A sibling test then applies it to `BulkArchiveDialog.java`: every `invokeLater` must carry a modality argument, and the old `CountDownLatch` hand-off must not come back.

### D6. Check every change before writing, and block those with a blocking sync result

`Archiver` gains `check(String changeName)`. It returns the blocking problems that `SpecSyncService.computeSync` reports for the change as `SpecSyncResult.warnings`. The warnings computeSync already produces are exactly the cases where it would skip a delta: a missing MODIFIED or REMOVED target, and an unpaired or unknown RENAMED entry. `archiveAll` runs in two phases:
1. It checks every selected change and marks any with problems as `Blocked: <first problem>`.
2. It syncs and archives the rest, in order.

`sync()` re-runs `computeSync` and throws if blockers have appeared since phase 1, because an earlier change in the batch can change a main spec. That row is then also shown as blocked, and nothing is written for it.

This mirrors upstream's archive and bulk-archive stop (1.14.0) and its check-every-target-first (1.13.1). A change with no delta specs has nothing to check. The later sync-fidelity change will add more refusals to `computeSync`, such as case-only collisions and scenario loss, and they will flow through this gate unchanged.

*Alternative considered:* aborting the whole batch when any change is blocked. The existing spec treats bulk archive as per-change, with conflicts as warnings and a failure not stopping the rest, so blocking per change keeps that contract.

## Risks / Trade-offs

- **`invokeAndWait` blocks the worker for each archive.** → That's intended: archives are sequential, and each waits for the previous one's VFS move anyway, which is what the latch did.
- **The test seams add package-private surface.** → Keep them minimal and annotated, in the style of the Settings panel's `@TestOnly` hooks.
- **`applySync`'s other callers change their hand-off.** → Their modality is non-modal either way, so behaviour is the same. `SpecSyncService` tests cover both the pooled-thread and the EDT paths.

## Migration Plan

Code-only. To roll back, revert the commit.
