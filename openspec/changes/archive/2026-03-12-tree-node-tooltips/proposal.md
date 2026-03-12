## Why

The spec tree in the tool window shows node labels but provides no hover context. Users must double-click to open a file or mentally decode status labels like "[blocked: proposal]" without explanation. Tooltips are a zero-cost discoverability layer — they appear on hover, don't require clicks, and are a standard IntelliJ convention that users expect from tree views.

## What Changes

- Register `ToolTipManager` on the spec tree so Swing renders tooltips
- Add a `tooltip` field to the `TreeNodeData` record so each node carries its own tooltip text
- Compute contextual tooltip content during tree model building for each node type:
  - **CHANGE**: status, file path
  - **ARTIFACT_READY**: description of what the artifact is, output path
  - **ARTIFACT_BLOCKED**: list of blocking dependencies
  - **ARTIFACT_DONE**: output file path
  - **SPEC_DOMAIN**: requirement count, file path
  - **REQUIREMENT**: RFC keyword (SHALL/SHOULD/MAY), scenario count
  - **DELTA_SPEC**: parent change, file path
  - **HINT**: actionable guidance (e.g., "Double-click to create")
- Set tooltip text in `SpecTreeCellRenderer` from `TreeNodeData`

## Capabilities

### New Capabilities
- `tree-node-tooltips`: Hover tooltips on all spec tree nodes showing contextual information

### Modified Capabilities
- `tool-window`: Add requirement that tree nodes display tooltips on hover

## Impact

- `SpecTreeModel.java` — extend `TreeNodeData` record with `tooltip` field, compute tooltips during `buildModel()`
- `SpecTreeCellRenderer.java` — set tooltip text in `getTreeCellRendererComponent()`
- `OpenSpecToolWindowPanel.java` — register `ToolTipManager` on tree
