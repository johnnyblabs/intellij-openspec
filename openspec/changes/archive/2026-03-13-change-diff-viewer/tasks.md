## 1. Diff Action

- [x] 1.1 Create `DeltaSpecDiffAction` that resolves the main spec path from the delta spec path, reads both file contents, and opens IntelliJ's DiffManager with a SimpleDiffRequest
- [x] 1.2 Handle the new-capability case where no main spec exists (show empty left content)

## 2. Context Menu Integration

- [x] 2.1 Add DELTA_SPEC case to the context menu switch in `OpenSpecToolWindowPanel.showContextMenu()` with "Preview Diff" and "Open File" actions

## 3. Verification

- [x] 3.1 Build and verify the context menu appears on delta spec nodes
- [x] 3.2 Verify diff viewer opens with correct left (main) and right (delta) content
- [x] 3.3 Verify new-capability case shows empty left panel with descriptive label
