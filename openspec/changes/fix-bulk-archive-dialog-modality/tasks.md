## 1. Dialog modality and status load

- [x] 1.1 Consult test-engineer (PLAN) for the D4 seams: how to supply `ChangeService`, `SpecSyncService` and `ArtifactOrchestrationService` headlessly, and how to construct the dialog in a light test. Verify: the plan is recorded and the seams below follow it. (Plan applied: formatting and a per-row guard inside `loadStatuses`; a production `Archiver.forProject` tested against a real light project; `onFinished` driven through the real task with an `Error`; `applySync` tested on real temp files; guards also cover `invokeAndWait` and fully-qualified latches; simulation-only tests removed.)
- [x] 1.2 Capture `dialogModality` after `init()` (D1). Post the status load's result with `invokeLater(r, dialogModality, ignored -> isDisposed())`, with the status computation in a method that takes the modality. Mark `ChangeRow`'s mutable fields `volatile`. Verify: a headless modal-state test shows each row's status changing from "loading..." to its completion before the modal is left; a negative control with `nonModal()` shows nothing until it is left. Mutation check: dropping the modality makes the first test fail.

## 2. Archive loop

- [x] 2.1 Extract the per-change body into `archiveAll(List<ChangeRow>, ModalityState)` (D4). Replace the archive `invokeLater` plus latch with `invokeAndWait(..., dialogModality)` (D2), keeping per-change failure handling and order. Verify: a headless test runs `archiveAll` from a pooled thread inside a modal state against two changes, one of which fails. It asserts "Done" and "Failed" while the modal is open, that the archived change's directory moved, and that processing continued past the failure. Mutation check: posting the archive step without the modality makes it time out. (Done: the in-modal loop test fails, and the guard flags the one-argument `invokeAndWait`.)
- [x] 2.2 Move OK re-enable and the summary notification into `onFinished()` (D3). Verify: a test of the completion handler shows OK re-enabled and the summary counted from the rows, including after a simulated failure in `run()`. Mutation check: moving the re-enable back into `run()`'s last line makes the failure case fail.

- [x] 2.3 Add the D6 gate: `Archiver.check`, a check-all-first phase in `archiveAll`, rows shown as `Blocked: <reason>`, and a re-check in `sync()`. Verify:
  - a fake-Archiver test shows a blocked change is neither synced nor archived while the others are, and that every `check` runs before any `sync`;
  - a real `SpecSyncService` test builds a change whose MODIFIED delta targets a missing requirement in a temp project, and `Archiver.forProject(...).check` returns the `[sync-modified-target-missing]` warning.

  Mutation check: skipping the check phase makes the first test fail.

## 3. Spec-sync hand-off

- [x] 3.1 Replace `SpecSyncService.applySync`'s refresh `invokeLater` plus latch with `invokeAndWait(..., ModalityState.defaultModalityState())` (D2). Verify: `SpecSyncServiceTest` (existing, plus a new case) passes when `applySync` is called from a pooled thread and from the EDT (where the old latch deadlocked); the existing sync tests stay green. (`SpecSyncApplyThreadingTest`, on real temp files with the captured 1.13.2 `renamed-star` content. Manual check: restoring the old latch made the EDT case hang until the time limit.)

## 4. Guard

- [x] 4.1 Generalize the invokeLater-modality scan from `SettingsPanelModalityHygieneTest` into a shared test helper (D5), and add a guard for `BulkArchiveDialog.java`: every `invokeLater` has a modality, and no `CountDownLatch` remains. Verify: both guards pass. Mutation check: removing one modality, or reintroducing a latch, fails the dialog guard.

## 5. Docs and verify

- [x] 5.1 Add a `### Fixed` CHANGELOG entry, user-facing only: the Bulk Archive dialog now shows each change's status while it's open, and always becomes closable when archiving ends. Verify: the entry is present with no internal or tracker wording; it also says Bulk Archive now holds back changes whose spec updates can't be applied cleanly.
- [x] 5.2 Sandbox pass (`./gradlew runIde` on a seeded demo project with two archivable changes): open Bulk Archive and see each status fill while it's open; archive both and see the rows go "Archiving..." to "Done", then OK enables and closes the dialog. Verify: each observed, recorded here when checked. **Sandbox results (2026-10-17, CLI 1.14.3, expanded workflow profile in an isolated CLI config):** the Status column filled while the dialog was open. Archiving the three changes left `demo-orphan` (a MODIFIED delta for a requirement its main spec lacks) Blocked with the reason, untouched and still active, and archived `demo-add-farewell` and `demo-ready` (Done; `demo-ready`'s requirement merged into the main spec). OK came back as Close, and the summary read 2/3. Bulk Archive needs the expanded workflow profile, so the plain demo greys it out by design.
- [x] 5.3 Run `./gradlew build`, `./gradlew verifyPlugin` and `openspec validate fix-bulk-archive-dialog-modality --strict`. Verify: all pass, with no new deprecated usages on the verified IDEs.
