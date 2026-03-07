# Menu and Actions Reference

All OpenSpec actions are available from the **OpenSpec** top-level menu and from the tool window toolbar. Some actions also appear in tree node context menus.

## Action Table

| Action | Menu Label | CLI Command | Built-in Fallback | Context Menu |
|--------|-----------|-------------|-------------------|--------------|
| **Init** | OpenSpec → Init | `openspec init` | ScaffoldingService creates structure | — |
| **Propose** | OpenSpec → Propose | `openspec propose` | ScaffoldingService + dialog | Toolbar |
| **Apply** | OpenSpec → Apply | *(none)* | Updates .openspec.yaml status | Change node |
| **Archive** | OpenSpec → Archive | `openspec archive` | Moves directory to archive/ | Change node |
| **Validate** | OpenSpec → Validate | `openspec validate` | BuiltInValidator | Toolbar, spec nodes |
| **List** | OpenSpec → List | `openspec list` | Parses files directly | — |
| **Refresh Tree** | OpenSpec → Refresh | *(none)* | Rebuilds SpecTreeModel | Toolbar |
| **Generate Artifact** | OpenSpec → Generate Artifact | `openspec artifact` | Builds prompt from DAG | Artifact nodes |
| **Generate All** | OpenSpec → Generate All Artifacts | `openspec artifact --all` | Walks DAG via API | Change node |

## Action Class Hierarchy

```
AnAction (IntelliJ Platform)
├── OpenSpecBaseAction (abstract)
│   ├── OpenSpecInitAction
│   ├── OpenSpecProposeAction
│   ├── OpenSpecApplyAction
│   ├── OpenSpecValidateAction
│   ├── GenerateArtifactAction
│   └── GenerateAllArtifactsAction
├── OpenSpecCliAction (abstract, extends OpenSpecBaseAction)
│   ├── OpenSpecArchiveAction
│   ├── OpenSpecListAction
│   └── OpenSpecRefreshAction
└── CreateDeltaSpecAction (direct AnAction)
```

## CLI Fallback Pattern

Most actions try the CLI first. If the CLI is unavailable, they fall back to built-in implementations:

1. Check `CliDetectionService.isAvailable()`
2. If available → run CLI command via `CliRunner`
3. If unavailable → call `handleCliMissing()` which provides built-in behavior
4. Display output in Console tab

**Exceptions:**
- **Apply** has no CLI command — always runs built-in
- **Refresh** is purely IDE-side
- **Generate Artifact/All** uses the CLI for DAG info but generates via DirectApiService or clipboard

## Visibility Rules

| Action | Visible When |
|--------|-------------|
| **Init** | Always (even in non-OpenSpec projects) |
| **All others** | Only when `isOpenSpecProject()` returns true |
| **Generate Artifact** | A change artifact is selected in tree |
| **Generate All** | AI provider is configured and API key is stored |

## DataKeys

Actions that operate on tree selections use `OpenSpecDataKeys`:

| Key | Type | Provided By |
|-----|------|-------------|
| `CHANGE_NAME` | `String` | Tree node selection |
| `ARTIFACT_ID` | `String` | Tree node selection |

These are supplied by `OpenSpecToolWindowPanel` via the `DataProvider` interface.

---

**Previous:** [[Tool-Window-Guide]] | **Next:** [[AI-Configuration]]
