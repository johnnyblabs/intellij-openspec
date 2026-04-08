## 1. Sync Specs Icon Button

- [x] 1.1 Add `syncSpecsIconButton` field to `WorkflowActionPanel` using `createIconButton(AllIcons.Actions.Download, "Sync Specs", this::onSyncSpecs)`
- [x] 1.2 Insert `syncSpecsIconButton` into the `iconButtons` panel between `verifyIconButton` and `archiveIconButton` (line ~224)
- [x] 1.3 Set initial enabled state to `false` and update in `refresh()` based on `hasDeltaSpecs`
- [x] 1.4 Add contextual tooltip logic: "Sync Specs: <change-name>" when enabled, "Sync Specs (no delta specs)" when disabled

## 2. Overflow Menu Cleanup

- [x] 2.1 Remove the Sync Specs menu item from `showOverflowMenu()`
- [x] 2.2 Handle empty menu state: if no generation in progress, either show nothing or suppress the menu popup

## 3. Archive Guard

- [x] 3.1 Add `hasDeltaSpecs` check at the top of `onArchive()`, before the progress task
- [x] 3.2 Show `Messages.showYesNoCancelDialog()` with title "Unsynced Delta Specs", message explaining the change has spec changes that haven't been synced, and options "Sync First" / "Archive Without Syncing" / "Cancel"
- [x] 3.3 On "Sync First": call `onSyncSpecs()` and return (do not proceed with archive)
- [x] 3.4 On "Archive Without Syncing": fall through to existing archive logic
- [x] 3.5 On "Cancel": re-enable archive button and return

## 4. Verification

- [ ] 4.1 Manual test: open a change with delta specs — verify Sync Specs button is enabled and positioned between Verify and Archive
- [ ] 4.2 Manual test: open a change without delta specs — verify Sync Specs button is disabled with correct tooltip
- [ ] 4.3 Manual test: click Archive with unsynced delta specs — verify guard dialog appears with three options
- [ ] 4.4 Manual test: click Archive without delta specs — verify archive proceeds directly without dialog
- [ ] 4.5 Manual test: verify overflow menu no longer contains Sync Specs
