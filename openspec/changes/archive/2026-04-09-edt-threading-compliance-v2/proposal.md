## Why

JetBrains Marketplace plugin review and internal testing identified 6 EDT threading violations — 2 critical deadlock risks (`invokeAndWait` from background tasks), 2 high-severity EDT-blocking actions (file/process I/O on the UI thread), and 2 medium VFS-refresh issues. These violate IntelliJ Platform threading rules and risk UI freezes or hard deadlocks in production.

## What Changes

- Replace `invokeAndWait` with `invokeLater` + callback chain in `WorkflowActionPanel` archive path and `BulkArchiveDialog` archive loop — eliminates deadlock when EDT is blocked on a modal
- Restructure `ChangeService.archiveChange` callers to separate the `WriteAction` (EDT) from surrounding background work via `invokeLater`
- Wrap `OpenSpecInitAction.actionPerformed` scaffolding/CLI call in `ProgressManager.Backgroundable` — moves process I/O off EDT
- Wrap `OpenSpecProposeAction.actionPerformed` file creation in `executeOnPooledThread` — moves VFS write operations off EDT
- Move `refreshAndFindFileByNioFile` before the `invokeLater` hop in `ExploreContextAction.deliverEditorTab` — VFS index lookup stays on the pooled thread
- Replace `invokeAndWait` with `invokeLater` in `SpecSyncService.applySync` VFS refresh loop — eliminates deadlock risk in tight loop

## Capabilities

### New Capabilities
- `edt-compliance`: Threading contract rules for EDT vs background thread usage across actions, services, and dialogs

### Modified Capabilities
- `bulk-archive`: Archive loop must not use `invokeAndWait` — async callback chain required
- `spec-sync`: VFS refresh in sync loop must use `invokeLater`, not `invokeAndWait`
- `workflow`: Archive action in WorkflowActionPanel must use `invokeLater` for `WriteAction` dispatch
- `explore-context`: Editor tab delivery must perform VFS refresh on background thread before EDT hop

## Impact

- **Files changed**: `WorkflowActionPanel.java`, `BulkArchiveDialog.java`, `OpenSpecInitAction.java`, `OpenSpecProposeAction.java`, `SpecSyncService.java`, `ExploreContextAction.java`
- **Behavioral change**: Archive, init, propose, sync, and explore actions become non-blocking — UI remains responsive during I/O
- **Risk**: Async conversion means callers can no longer assume operation is complete on return — post-archive refresh and post-sync validation must chain via callbacks
- **No API or dependency changes**