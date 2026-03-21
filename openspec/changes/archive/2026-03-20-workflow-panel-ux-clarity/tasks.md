## 1. Tree-to-panel synchronization

- [x] 1.1 Add `resolveChangeName(TreePath)` method to `SpecTreeModel` — walk up from any node to find the parent change name, return null for non-change nodes
- [x] 1.2 Add `TreeSelectionListener` in `OpenSpecToolWindowPanel` that calls `resolveChangeName()` and updates the workflow panel via `setActiveChange(changeName)`
- [x] 1.3 Add `setActiveChange(String changeName)` public method to `WorkflowActionPanel` — sets the dropdown, refreshes pipeline
- [x] 1.4 Ensure sync is one-way: tree drives panel, panel dropdown changes do NOT update tree selection

## 2. Icon bar restructure

- [x] 2.1 Remove Fast-Forward icon button from the icon bar
- [x] 2.2 Add a muted change-name label at the left edge of the icon bar (JBLabel with secondary foreground color)
- [x] 2.3 Update `updateIconBarState()` to set the label text to the active change name

## 3. Contextual tooltips

- [x] 3.1 Update Verify icon tooltip: enabled → "Verify: <change-name>", disabled → "Verify (complete all artifacts first)"
- [x] 3.2 Update Archive icon tooltip: enabled → "Archive: <change-name>", disabled → "Archive (complete all artifacts and tasks first)"
- [x] 3.3 Update overflow menu icon tooltip to "More actions..." (already set at creation)

## 4. Overflow menu reorganization

- [x] 4.1 Rename "Bulk Archive" to "Archive All Changes..."
- [x] 4.2 Add separator before "Archive All Changes..."
- [x] 4.3 Move "Start New Change" and add "Fast-Forward..." below a second separator labeled as creation actions
- [x] 4.4 Disable "Archive All Changes..." when fewer than 2 active changes exist

## 5. Tests

- [x] 5.1 Test `resolveChangeName()` returns correct change name for change nodes, child nodes, and null for non-change nodes
- [x] 5.2 Test icon bar does not contain FF button
- [x] 5.3 Test tooltip text updates with change name when enabled/disabled
- [x] 5.4 Test overflow menu structure has correct separators and labels
