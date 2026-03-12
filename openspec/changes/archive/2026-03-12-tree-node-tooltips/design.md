## Context

The spec tree (`SpecTreeModel` → `SpecTreeCellRenderer` → `OpenSpecToolWindowPanel`) currently provides visual differentiation via colors, fonts, and icons, but no hover tooltips. The `TreeNodeData` record carries `label`, `type`, `filePath`, `changeName`, and `artifactId`. The tree uses IntelliJ's `Tree` component (extends JTree) with a custom `DefaultTreeCellRenderer`.

## Goals / Non-Goals

**Goals:**
- Every tree node shows a meaningful tooltip on hover
- Tooltips provide contextual information not visible in the label (file paths, descriptions, counts, guidance)
- Implementation uses standard Swing/IntelliJ tooltip mechanisms

**Non-Goals:**
- Rich HTML tooltips with links or formatting — plain text is sufficient for v0.1.0
- Tooltip content that requires additional I/O (e.g., reading file contents at hover time)
- Custom tooltip rendering or positioning

## Decisions

### 1. Add `tooltip` field to `TreeNodeData` record
Extend the record to `TreeNodeData(label, type, filePath, changeName, artifactId, tooltip)`. Compute tooltip text during `buildModel()` when all data is already in memory. This avoids lazy computation at render time.

**Alternative considered:** Compute tooltips in the renderer — rejected because the renderer doesn't have access to metadata like requirement counts or artifact descriptions.

### 2. Set tooltip in `SpecTreeCellRenderer.getTreeCellRendererComponent()`
After extracting `TreeNodeData`, call `setToolTipText(data.tooltip())`. This is the standard Swing pattern for tree tooltips.

### 3. Register `ToolTipManager` on tree in `OpenSpecToolWindowPanel`
Call `ToolTipManager.sharedInstance().registerComponent(tree)` after tree creation. Without this, Swing ignores tooltip text on tree cells.

### 4. Tooltip content per node type

| Node Type | Tooltip Content |
|-----------|----------------|
| SPECS | "Capability specifications" |
| SPEC_DOMAIN | "{N} requirements — {filePath}" |
| REQUIREMENT | "Requirement: {name}" (full name without truncation) |
| CHANGES | "Active changes" |
| CHANGE | "{filePath}" |
| ARTIFACT | "{filePath}" |
| ARTIFACT_DONE | "Complete — {filePath}" |
| ARTIFACT_READY | "Ready to generate" |
| ARTIFACT_BLOCKED | "Blocked by: {dep1}, {dep2}" |
| MISSING_ARTIFACT | "Not yet created" |
| DELTA_SPEC | "Delta spec — {filePath}" |
| ARCHIVE | "Completed changes" |
| HINT | Repeat the hint label (already descriptive) |

## Risks / Trade-offs

- [Risk] Record change breaks existing constructors → Mitigation: Add new canonical constructor with tooltip, update existing 3-arg and 5-arg constructors to pass null tooltip (backward-compatible)
- [Risk] Tooltips add visual noise → Mitigation: Tooltips only appear on hover, no permanent UI change
