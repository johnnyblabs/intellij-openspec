## Why

The Bulk Archive dialog has the same background-to-UI handoff problem that was fixed in Settings, plus two related defects. A platform analysis against the 2024.2 sources confirmed each one:

1. **The Status column never fills while the dialog is open.** Each change's artifact status is computed on a background thread and posted back with `invokeLater` and no modality. Inside the modal dialog that update is held until the dialog closes, so every row shows "loading..." for as long as the user can see it, even though the dialog exists to help them decide what to archive.
2. **The dialog can get permanently stuck.** While archiving runs, OK and Cancel are disabled, and Escape and the window's close button route through Cancel too, so they're blocked as well. OK is re-enabled only by the last statement of the background loop. Anything that throws outside the per-change error handling skips it, leaving a dialog the user can't close.
3. **The archive loop works only by accident.** Its `invokeLater` plus latch hand-offs, here and in `SpecSyncService.applySync`, happen to inherit the dialog's modality because the task is started on the UI thread inside the dialog. Started any other way, they would stall forever. In unit-test mode they deadlock, which is why the loop has never been tested. The existing tests only simulate its logic.

4. **Bulk Archive archives changes whose spec sync reports a blocking problem.** If a change's MODIFIED delta targets a requirement the main spec doesn't have, sync records a warning and skips that delta, and Bulk Archive archives the change anyway, so the delta is silently lost. Upstream OpenSpec stops archive and bulk archive in this case (1.14.0), and checks every target before writing any spec (1.13.1); a release-notes audit found the plugin never mirrored this.

The spec also mandates `invokeLater` and forbids `invokeAndWait` for the archive step, and describes an `AtomicInteger` completion counter the code doesn't have.

Tracked in the linked tracker entry.

## What Changes

- **Status fills while the dialog is open.** The dialog captures its own modality once, after it's built (`ModalityState.stateForComponent` of its content panel; the dialog window already exists at that point). Every background result is posted with that modality and is dropped if the dialog has been closed.
- **The archive step uses `invokeAndWait` with the dialog's modality,** replacing `invokeLater` plus a latch. Each archive moves a change directory in a write action, which must run on the UI thread, so the hop stays. It's now explicit rather than inherited, and it runs inline when already on the UI thread. That makes the loop testable.
- **The spec-sync refresh in `applySync` uses the same explicit `invokeAndWait`** instead of `invokeLater` plus a latch. All three callers keep working: Bulk Archive, the Sync action and the workflow panel.
- **The dialog can always be closed when archiving ends.** Re-enabling OK and the summary notification move into the background task's guaranteed completion callback, so they happen even if the loop fails part-way.
- **Bulk Archive checks before it writes.** Before writing anything, it runs the spec-sync check for every selected change. A change whose sync reports a blocking problem (a missing MODIFIED or REMOVED target, an unpaired or unknown RENAMED entry) is shown as **Blocked** with the reason, and nothing is written for it. The others are archived. The check runs again just before each change is written, because an earlier archive in the same batch can change a main spec.
- **Rows read by the table and written by the background thread are made safe to share** (`volatile`).
- **Tests:** the archive loop is extracted so it can run from a background thread inside a simulated modal state. The test shows the rows reach "Done" while the dialog is open, plus a negative control. The status load gets the same treatment. The simulation-only tests are replaced or supplemented with tests that drive the real code.
- **Out of scope:** conflict detection still reads delta specs on the UI thread on every checkbox toggle. That's a responsiveness issue, not a modality one, so it isn't changed here.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `bulk-archive`: the archive requirement no longer prescribes `invokeLater` and forbids `invokeAndWait`; it requires sequential archiving that stays responsive and always ends in a closable dialog. New requirements cover the Status column filling while the dialog is open, and blocking changes whose spec sync reports a problem.

## Impact

- **Code:** `BulkArchiveDialog` (modality capture, status load, archive loop, completion callback, row fields) and `SpecSyncService.applySync` (refresh hand-off).
- **Tests:** new headless modal-state tests for the status load and the archive loop, a guard against `invokeLater` without a modality in `BulkArchiveDialog`, and `SpecSyncService` coverage for the new hand-off from both a background thread and the UI thread.
- **Docs:** a CHANGELOG `### Fixed` entry; the `bulk-archive` spec.
- **Platforms:** IntelliJ 2024.2+ compatibility unchanged. `ModalityState.stateForComponent`, `invokeLater(Runnable, ModalityState, Condition)` and `invokeAndWait(Runnable, ModalityState)` are stable and non-deprecated from 2024.2 through 2026.2.
