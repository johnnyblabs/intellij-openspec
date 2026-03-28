# Actions and Commands

This page covers the action system: class hierarchy, the CLI fallback pattern, data context, and how actions interact with services.

## Class Hierarchy

### OpenSpecBaseAction

Abstract base class for all OpenSpec actions.

**Responsibilities:**
- `update()` — hides action if project is not OpenSpec-enabled, then checks `getWorkflowId()` against `WorkflowProfileService` to disable actions not in the active profile (visible but disabled with tooltip)
- `getWorkflowId()` — returns `null` for utility actions (always enabled) or a workflow string (e.g., `"ff"`, `"verify"`) for workflow-bound actions
- Provides `refreshToolWindow()` helper for tree refresh after changes

### OpenSpecCliAction

Extends `OpenSpecBaseAction` for read-only actions backed by CLI commands.

**Template method pattern:**
```
actionPerformed(event)
  → if CLI available: runCliCommand()
  → else: handleCliMissing(project)
```

**Abstract methods subclasses implement:**
| Method | Purpose |
|--------|---------|
| `getCliArgs()` | Returns the CLI argument array |
| `getCommandLabel()` | Returns a display label for the command |
| `handleCliMissing(Project)` | Built-in fallback when CLI unavailable |

### Action Details

| Action | Base | Workflow ID | Notes |
|--------|------|-------------|-------|
| `OpenSpecInitAction` | BaseAction | *(none)* | Available even when not OpenSpec project |
| `OpenSpecProposeAction` | BaseAction | `propose` | Shows `ProposeChangeDialog` for name/description |
| `ExploreContextAction` | BaseAction | `explore` | Topic dialog + delivery routing (Direct API, Editor Tab, Clipboard). Explore tab only appears when Direct API is configured; panel submits always use Direct API. |
| `OpenSpecApplyAction` | BaseAction | `apply` | Focuses workflow panel for apply prompt delivery |
| `OpenSpecArchiveAction` | BaseAction | `archive` | ComplianceService pre-flight + move to archive/ |
| `OpenSpecFfAction` | BaseAction | `ff` | Delegates to WorkflowActionPanel FF input |
| `OpenSpecContinueAction` | BaseAction | `continue` | DirectApiService artifact generation |
| `OpenSpecVerifyAction` | BaseAction | `verify` | VerificationService report dialog |
| `OpenSpecSyncAction` | BaseAction | `sync` | SpecSyncService preview dialog |
| `OpenSpecBulkArchiveAction` | BaseAction | `bulk-archive` | Multi-select dialog, requires 2+ active changes |
| `OpenSpecValidateAction` | BaseAction | *(none)* | Merges built-in + CLI validation results |
| `OpenSpecManageToolsAction` | BaseAction | *(none)* | ManageAiToolsDialog, pre-init OK |
| `OpenSpecSetupWizardAction` | BaseAction | *(none)* | Guided onboarding dialog |
| `OpenSpecListAction` | CliAction | *(none)* | Falls back to file parsing |
| `OpenSpecUpdateAction` | CliAction | *(none)* | CLI only, no built-in fallback |
| `CreateDeltaSpecAction` | AnAction | *(n/a)* | Dynamic context menu; creates file in change dir |
| `DeltaSpecDiffAction` | AnAction | *(n/a)* | Dynamic context menu; opens diff viewer |

## DataKeys

`OpenSpecDataKeys` provides typed data context keys used by the tree panel:

```java
DataKey<String> CHANGE_NAME   // Selected change name
DataKey<String> ARTIFACT_ID   // Selected artifact ID
```

**Flow:**
1. User clicks a tree node in `OpenSpecToolWindowPanel`
2. Panel implements `DataProvider` and supplies keys based on `TreeNodeData`
3. Action reads keys via `event.getData(OpenSpecDataKeys.CHANGE_NAME)`

## Console Integration

Actions that produce output use `OpenSpecConsoleService`:

1. Get console via `project.getService(OpenSpecConsoleService.class)`
2. Call `getAndActivate()` to switch to Console tab
3. Print output: `printCommand()`, `printOutput()`, `printError()`

## Background Execution

Long-running operations (CLI calls, API requests) use IntelliJ's background task system:

```java
Task.Backgroundable task = new Task.Backgroundable(project, "Running...") {
    public void run(ProgressIndicator indicator) {
        // CLI or API call here
    }
};
ProgressManager.getInstance().run(task);
```

This prevents UI freezing during CLI execution or AI API calls.

## Context Menu Actions

The tool window panel builds context menus dynamically based on `TreeNodeType`:

| Node Type | Actions Shown |
|-----------|--------------|
| `CHANGE` | Apply, Archive, Generate All |
| `ARTIFACT_READY` | Generate Artifact |
| `ARTIFACT_BLOCKED` | *(none — disabled)* |
| `SPEC_DOMAIN` | Validate |
| `DELTA_SPEC` | Open in Editor |

---

**Previous:** [[Service-Layer]] | **Next:** [[Data-Model]]
