## 1. Change Selector

- [x] 1.1 Add a change selector to WorkflowActionPanel that shows a JComboBox when multiple active changes exist, or a plain JBLabel when only one exists
- [x] 1.2 Update `refresh()` to populate the selector from `ChangeService.getActiveChanges()` and preserve the current selection
- [x] 1.3 Auto-select a new change when proposed (if no selection exists) and deselect when archived
- [x] 1.4 Wire the selector's selection change to update the panel's `activeChangeName`, pipeline, and Generate button

## 2. Artifact Pipeline Visualization

- [x] 2.1 Add a pipeline status row (JPanel with FlowLayout) between the change selector and the Generate button showing each artifact as a labeled chip with state icon
- [x] 2.2 Create a `PipelineChip` helper that renders artifact id + state indicator (✓ done, ● ready, ○ blocked)
- [x] 2.3 Update `refresh()` to rebuild pipeline chips from the DAG whenever the selected change updates

## 3. Remove Standalone Generate Actions

- [x] 3.1 Remove `GenerateArtifact` and `GenerateAll` action entries from `plugin.xml` (both MainMenu group and ToolWindowToolbar group)
- [x] 3.2 Delete `GenerateArtifactAction.java` and `GenerateAllArtifactsAction.java`
- [x] 3.3 Remove generate-related context menu entries from `OpenSpecToolWindowPanel.showContextMenu()` for CHANGE, ARTIFACT_READY, and ARTIFACT_DONE node types
- [x] 3.4 Add a "Generate..." context menu entry on CHANGE nodes that sets the panel's active change and triggers generation

## 4. Cleanup and Verification

- [x] 4.1 Update any remaining references to the removed actions (imports, data keys if unused)
- [x] 4.2 Verify the plugin builds and all tests pass
- [x] 4.3 Verify the tool window renders correctly: change selector, pipeline chips, Generate button, no generate actions in toolbar
