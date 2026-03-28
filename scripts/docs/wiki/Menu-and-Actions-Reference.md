# Menu and Actions Reference

All OpenSpec actions are available from the **OpenSpec** top-level menu and from the tool window toolbar. Some actions also appear in tree node context menus.

## Action Table

| Action | Menu Label | Workflow ID | Built-in Fallback | Context Menu |
|--------|-----------|-------------|-------------------|--------------|
| **Init** | OpenSpec → Init | *(none)* | ScaffoldingService creates structure | — |
| **Propose** | OpenSpec → Propose... | `propose` | ScaffoldingService + dialog | Toolbar |
| **Fast-Forward** | OpenSpec → Fast-Forward... | `ff` | Delegates to WorkflowActionPanel | — |
| **Explore** | OpenSpec → Explore... | `explore` | Topic dialog + delivery routing. Explore tab requires Direct API. | — |
| **Continue** | OpenSpec → Continue | `continue` | DirectApiService artifact generation | — |
| **Apply** | OpenSpec → Apply | `apply` | Focuses workflow panel for apply | Change node |
| **Verify** | OpenSpec → Verify | `verify` | VerificationService report dialog | — |
| **Sync Specs** | OpenSpec → Sync Specs | `sync` | SpecSyncService preview dialog | — |
| **Archive** | OpenSpec → Archive | `archive` | ComplianceService + move to archive/ | Change node |
| **Bulk Archive** | OpenSpec → Bulk Archive... | `bulk-archive` | BulkArchiveDialog with conflict detection | — |
| **Validate** | OpenSpec → Validate | *(none)* | BuiltInValidator + CLI merge | Toolbar, spec nodes |
| **List** | OpenSpec → List | *(none)* | Parses files directly / CLI fallback | — |
| **Refresh** | OpenSpec → Refresh | *(none)* | Rebuilds SpecTreeModel | Toolbar |
| **Update** | OpenSpec → Update OpenSpec | *(none)* | CLI only (`openspec update`) | — |
| **Manage AI Tools** | OpenSpec → Manage AI Tools... | *(none)* | ManageAiToolsDialog | — |
| **Setup Wizard** | OpenSpec → Setup Wizard... | *(none)* | SetupWizardDialog | — |

## Action Class Hierarchy

```
AnAction (IntelliJ Platform)
├── OpenSpecBaseAction (abstract)
│   │   getWorkflowId() → null (utility) or workflow string (workflow-bound)
│   │   update() → profile-aware enablement check
│   ├── OpenSpecInitAction
│   ├── OpenSpecProposeAction          → "propose"
│   ├── ExploreContextAction           → "explore"
│   ├── OpenSpecApplyAction            → "apply"
│   ├── OpenSpecArchiveAction          → "archive"
│   ├── OpenSpecFfAction               → "ff"
│   ├── OpenSpecContinueAction         → "continue"
│   ├── OpenSpecVerifyAction           → "verify"
│   ├── OpenSpecSyncAction             → "sync"
│   ├── OpenSpecBulkArchiveAction      → "bulk-archive"
│   ├── OpenSpecValidateAction
│   ├── OpenSpecManageToolsAction
│   ├── OpenSpecSetupWizardAction
│   └── OpenSpecCliAction (abstract)
│       ├── OpenSpecListAction
│       └── OpenSpecUpdateAction
├── CreateDeltaSpecAction (direct AnAction)
└── DeltaSpecDiffAction (direct AnAction)
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

## Visibility & Enablement Rules

Actions use a two-tier check in `update()`:

1. **Visibility**: All actions (except Init, ManageTools) are hidden in non-OpenSpec projects
2. **Enablement**: Actions with a `getWorkflowId()` are disabled if their workflow is not in the active profile

| Action | Visible When | Enabled When |
|--------|-------------|-------------|
| **Init** | Always | Always |
| **ManageTools** | Always | Always |
| **Workflow actions** (Propose, Explore, Apply, Archive, FF, Continue, Verify, Sync, Bulk Archive) | OpenSpec project | Workflow ID in active profile |
| **Utility actions** (Validate, List, Refresh, Update, SetupWizard) | OpenSpec project | Always |
| **Bulk Archive** | OpenSpec project | Workflow in profile AND 2+ active changes |

Disabled workflow actions show tooltip: *"Requires expanded profile. Change in Settings → Tools → OpenSpec."*

## DataKeys

Actions that operate on tree selections use `OpenSpecDataKeys`:

| Key | Type | Provided By |
|-----|------|-------------|
| `CHANGE_NAME` | `String` | Tree node selection |
| `ARTIFACT_ID` | `String` | Tree node selection |

These are supplied by `OpenSpecToolWindowPanel` via the `DataProvider` interface.

---

**Previous:** [[Tool-Window-Guide]] | **Next:** [[AI-Configuration]]
