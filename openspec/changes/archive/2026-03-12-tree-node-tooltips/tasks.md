## 1. TreeNodeData: Add Tooltip Field

- [x] 1.1 Add `tooltip` parameter to `TreeNodeData` record's canonical constructor
- [x] 1.2 Update the 3-arg backward-compatible constructor to pass `null` tooltip
- [x] 1.3 Update the 5-arg backward-compatible constructor to pass `null` tooltip

## 2. SpecTreeModel: Compute Tooltips During Build

- [x] 2.1 Add tooltips to section nodes (SPECS: "Capability specifications", CHANGES: "Active changes", ARCHIVE: "Completed changes")
- [x] 2.2 Add tooltips to SPEC_DOMAIN nodes ("{N} requirements — {filePath}")
- [x] 2.3 Add tooltips to REQUIREMENT nodes (full requirement name)
- [x] 2.4 Add tooltips to CHANGE nodes (change directory path)
- [x] 2.5 Add tooltips to DAG artifact nodes (DONE: "Complete — {path}", READY: "Ready to generate", BLOCKED: "Blocked by: {deps}")
- [x] 2.6 Add tooltips to fallback artifact nodes (file path) and MISSING_ARTIFACT ("Not yet created")
- [x] 2.7 Add tooltips to DELTA_SPEC nodes ("Delta spec — {filePath}")
- [x] 2.8 Add tooltips to HINT nodes (repeat the hint label)

## 3. SpecTreeCellRenderer: Set Tooltip Text

- [x] 3.1 In `getTreeCellRendererComponent()`, call `setToolTipText(data.tooltip())` when `TreeNodeData` is present

## 4. OpenSpecToolWindowPanel: Register ToolTipManager

- [x] 4.1 Call `ToolTipManager.sharedInstance().registerComponent(tree)` after tree creation

## 5. Verification

- [x] 5.1 Build compiles with no errors
- [x] 5.2 All existing tests pass
- [x] 5.3 Verify tooltips appear on hover for spec domain, change, and artifact nodes
