# UI Components

This page documents the tool window, tree model, cell renderer, console, and dialog components.

## OpenSpecToolWindowFactory

Implements `ToolWindowFactory` and `DumbAware`.

**Creates two content tabs:**
1. **Browse** — `OpenSpecToolWindowPanel` (tree view)
2. **Console** — `OpenSpecConsolePanel` (CLI output)

Registers the console panel with `OpenSpecConsoleService` at creation time. The factory is registered in `plugin.xml` with anchor `right` and the OpenSpec icon.

## OpenSpecToolWindowPanel

The main tree-based UI component. Extends `JPanel` with `BorderLayout`.

### Layout
```
┌──────────────────────────────┐
│  Toolbar (actions)           │
├──────────────────────────────┤
│                              │
│  JTree (SpecTreeModel)       │
│                              │
│                              │
├──────────────────────────────┤
│  Status bar (CLI + AI info)  │
└──────────────────────────────┘
```

### Features

| Feature | Implementation |
|---------|---------------|
| **Double-click** | Opens file in editor via `FileEditorManager` |
| **Right-click** | Context menu built from `TreeNodeData.type` |
| **DataProvider** | Supplies `CHANGE_NAME` and `ARTIFACT_ID` to actions |
| **File listener** | Watches `openspec/` for VFS changes |
| **Auto-refresh** | Debounced 300ms rebuild when files change |
| **Status bar** | Shows CLI version and detected AI tools |

### Data Provider

Implements `DataProvider` to make tree selection data available to actions:

```java
@Override
public Object getData(@NotNull String dataId) {
    if (OpenSpecDataKeys.CHANGE_NAME.is(dataId)) {
        return selectedNode.changeName;
    }
    if (OpenSpecDataKeys.ARTIFACT_ID.is(dataId)) {
        return selectedNode.artifactId;
    }
    return null;
}
```

## SpecTreeModel

Builds a `DefaultTreeModel` representing the entire OpenSpec project.

### Tree Structure

Three root branches:
1. **Specs** — organized by domain
2. **Changes** — active changes with artifacts
3. **Archive** — archived changes

### TreeNodeType (enum)

| Value | Purpose |
|-------|---------|
| `SPECS` | Root of specs section |
| `SPEC_DOMAIN` | Domain folder |
| `REQUIREMENT` | Individual requirement |
| `CHANGES` | Root of changes section |
| `CHANGE` | Single change (with status) |
| `ARTIFACT_DONE` | Completed artifact |
| `ARTIFACT_READY` | Ready to generate |
| `ARTIFACT_BLOCKED` | Blocked by dependencies |
| `MISSING_ARTIFACT` | Expected but missing |
| `DELTA_SPEC` | Delta spec file |
| `ARCHIVE` | Root of archive section |
| `HINT` | Informational message |

### TreeNodeData (record)

```java
record TreeNodeData(
    String label,
    TreeNodeType type,
    String filePath,
    String changeName,
    String artifactId
)
```

### Build Logic

The model is built asynchronously on a pooled thread:

1. Parse all specs via `SpecParsingService`
2. Load active changes via `ChangeService`
3. For each change:
   - If CLI available → fetch `ChangeArtifactDag` for DAG-based view
   - If CLI unavailable → list files and detect missing artifacts
4. Load archived changes
5. Swap the model on the EDT

## SpecTreeCellRenderer

Custom `DefaultTreeCellRenderer` that provides visual differentiation.

### Icon Mapping

| Node Type | Icon |
|-----------|------|
| Root | `openspec.svg` |
| Specs | `spec.svg` |
| Changes | `change.svg` |
| Requirement | `requirement.svg` |
| Archive | `archive.svg` |

### Color and Font Rules

| Node Type / Status | Color | Font |
|--------------------|-------|------|
| Change `[proposed]` | Green | Normal |
| Change `[applied]` | Blue | Normal |
| `ARTIFACT_DONE` | Green | Normal |
| `ARTIFACT_READY` | Blue | **Bold** |
| `ARTIFACT_BLOCKED` | Gray | *Italic* |
| `MISSING_ARTIFACT` | Gray | *Italic* |
| `HINT` | Gray | *Italic* |

## OpenSpecConsolePanel

Wraps IntelliJ's `ConsoleView` for displaying CLI command output.

### Methods

| Method | Content Type | Purpose |
|--------|-------------|---------|
| `printCommand(String)` | `SYSTEM_OUTPUT` | Shows the CLI command being run |
| `printOutput(String)` | `NORMAL_OUTPUT` | Shows stdout |
| `printError(String)` | `ERROR_OUTPUT` | Shows stderr |
| `printSystem(String)` | `SYSTEM_OUTPUT` | Shows system messages |
| `clear()` | — | Clears the console |

## OpenSpecConsoleService

Project-level service for accessing the console from anywhere.

| Method | Description |
|--------|-------------|
| `register(OpenSpecConsolePanel)` | Called by factory at creation |
| `getAndActivate()` | Returns panel and switches to Console tab |

## ProposeChangeDialog

Modal dialog for creating a new change.

**Fields:**
- **Change name** — text field (validated for directory-safe characters)
- **Description** — text area (optional)

**Returns:** change name and description, or null if cancelled.

---

**Previous:** [[Data-Model]] | **Next:** [[Build-and-Dev-Setup]]
