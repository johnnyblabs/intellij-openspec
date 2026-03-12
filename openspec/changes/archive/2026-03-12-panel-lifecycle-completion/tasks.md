## 1. Per-Change Validation in BuiltInValidator

- [x] 1.1 Extract the per-change validation logic from the `validateChanges()` for-loop body into a new public `validateChange(String changeName)` method that finds the change by name and validates it individually.
- [x] 1.2 Refactor `validateChanges()` to call `validateChange()` for each active change, collecting results.

## 2. Archive Button in Workflow Panel

- [x] 2.1 Add an `archiveButton` field (JButton) to WorkflowActionPanel, styled with `AllIcons.Actions.MoveTo` icon and "Archive" text.
- [x] 2.2 Wire the archive button's action listener to call `ChangeService.archiveChange()` followed by `ArchiveSyncService.syncAsync()`, disabling the button and showing "Archiving..." during the operation.
- [x] 2.3 In `showApplyState()`, when all tasks are complete: hide the generate button, hide the apply button, and show the archive button instead of the disabled "All complete" state.
- [x] 2.4 In `onTaskFileChanged()`, when all tasks become complete: hide the generate button, hide the apply button, show the archive button, and remove the "Consider archiving" notification in favor of the inline button.

## 3. Post-Archive Confirmation State

- [x] 3.1 Add a `showPostArchiveState(String changeName, boolean syncSuccess)` method that displays: a success message in the guidance area, pipeline chips in all-done state, and a "Start New Change" button in the action row.
- [x] 3.2 If sync failed, show a "Retry Sync" button alongside the success message that calls `ArchiveSyncService.syncAsync()` again.
- [x] 3.3 Wire the "Start New Change" button to trigger `ActionManager.getInstance().getAction("OpenSpec.Propose")` via `ActionUtil.invokeAction()`.
- [x] 3.4 Call `showPostArchiveState()` from the archive button's completion callback (after sync completes or is skipped).

## 4. Auto-Validation at Phase Transitions

- [x] 4.1 In `showApplyState()`, after determining all artifacts are DONE and tasks exist, call `validateChange()` on a pooled thread and show results in the guidance area via `SwingUtilities.invokeLater()`.
- [x] 4.2 In `onTaskFileChanged()` (or the archive-ready path), after all tasks complete, call `validateChange()` on a pooled thread and show results in the guidance area.
- [x] 4.3 Show validation results as: "✓ Change valid" (green) for pass, "⚠ N warnings" (amber) with the first warning description for warnings, "✗ N errors" (red) for errors.

## 5. Verify

- [x] 5.1 Run `./gradlew compileJava` and confirm clean compilation.
- [x] 5.2 Run `./gradlew clean test` and confirm all tests pass.
