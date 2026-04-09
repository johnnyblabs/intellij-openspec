## 1. Critical — Deadlock Fixes (invokeAndWait → invokeLater)

- [x] 1.1 Replace `invokeAndWait` with `invokeLater` in `WorkflowActionPanel.java` archive path (~line 1512). Move `archiveChange`, popover, and refresh into a single `invokeLater` block. Wrap body in try/catch with `OpenSpecNotifier.error` for failures and re-enable archive button on error.
- [x] 1.2 Replace `invokeAndWait` with `invokeLater` in `BulkArchiveDialog.java` archive loop (~line 220). Add `CountDownLatch` per iteration. Each `invokeLater` block archives one change, updates table model, counts down latch. Background thread awaits latch before next iteration.
- [x] 1.3 Replace `invokeAndWait` with `invokeLater` + `CountDownLatch` in `SpecSyncService.applySync` (~line 211). Each iteration creates a latch, posts VFS refresh via `invokeLater { WriteAction.run { ... }; latch.countDown() }`, then `latch.await()` on the background thread before proceeding to `validatePostMerge`.

## 2. High — EDT-Blocking Action Fixes

- [x] 2.1 Wrap `OpenSpecInitAction.actionPerformed` in `ProgressManager.Backgroundable`. Background thread runs CLI detection. EDT dispatch via `invokeLater` runs `scaffolding.initOpenSpec()` (needs WriteAction), then chains notification and `refreshToolWindow`. Error handling via try/catch inside `invokeLater` with `OpenSpecNotifier.error`.
- [x] 2.2 Wrap `OpenSpecProposeAction.actionPerformed` post-dialog work in `executeOnPooledThread`. From pooled thread, post `invokeLater` to run `createChange` (needs WriteAction), notification, `refreshToolWindow`, and `autoFocusChange` inside the same EDT dispatch. Error handling via try/catch with `OpenSpecNotifier.error`.

## 3. Medium — VFS Threading Fix

- [x] 3.1 In `ExploreContextAction.deliverEditorTab` (~line 140), move `refreshAndFindFileByNioFile` call before the `invokeLater` block so it executes on the pooled thread. Only `FileEditorManager.openFile` remains inside the `invokeLater`.

## 4. Testing

- [x] 4.1 Run `./gradlew build` to verify compilation and existing tests pass after all changes
- [ ] 4.2 Manual test: archive a change while a modal dialog (e.g., Settings) is open — verify no deadlock, archive completes, popover appears
- [ ] 4.3 Manual test: bulk archive 2+ changes — verify sequential completion, per-row status updates, summary notification
- [ ] 4.4 Manual test: run Init action in a non-OpenSpec project — verify UI stays responsive, success notification appears
- [ ] 4.5 Manual test: run Propose action — verify UI stays responsive, change appears in tree, auto-focus works
- [ ] 4.6 Manual test: Sync Specs on a change with multiple delta specs — verify VFS refresh completes, post-merge validation runs
- [ ] 4.7 Manual test: Explore → Editor Tab delivery — verify file opens in editor without EDT lag

## 5. Release Prep

- [x] 5.1 Update `changeNotes` in `build.gradle.kts` — this was already done for v0.2.8 but will need v0.2.9 entry
- [x] 5.2 Add v0.2.9 entry to `CHANGELOG.md` with threading compliance summary
- [x] 5.3 Bump version to `0.2.9` in `build.gradle.kts` (version lives here, not gradle.properties)
