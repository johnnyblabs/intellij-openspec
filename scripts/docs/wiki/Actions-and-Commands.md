# Actions and Commands

This page covers the action system: class hierarchy, the CLI fallback pattern, data context, and how actions interact with services.

## Class Hierarchy

### OpenSpecBaseAction

Abstract base class for all OpenSpec actions.

**Responsibilities:**
- `update()` — disables action if project is not OpenSpec-enabled (except Init)
- Provides helper methods to access services from `AnActionEvent`

### OpenSpecCliAction

Extends `OpenSpecBaseAction` for actions backed by CLI commands.

**Template method pattern:**
```
actionPerformed(event)
  → if CLI available: runCliCommand()
  → else: handleCliMissing(project)
```

**Abstract methods subclasses implement:**
| Method | Purpose |
|--------|---------|
| `getCliCommand()` | Returns the CLI command string (e.g., `"archive"`) |
| `handleCliMissing(Project)` | Built-in fallback when CLI unavailable |
| `processOutput(String)` | Optional processing of CLI stdout |

### Action Details

| Action | Base | CLI Command | Notes |
|--------|------|-------------|-------|
| `OpenSpecInitAction` | BaseAction | `openspec init` | Available even when not OpenSpec project |
| `OpenSpecProposeAction` | BaseAction | `openspec propose` | Shows `ProposeChangeDialog` for name/description |
| `OpenSpecApplyAction` | BaseAction | *(none)* | Always built-in; updates `.openspec.yaml` |
| `OpenSpecValidateAction` | BaseAction | `openspec validate` | Merges built-in + CLI results |
| `GenerateArtifactAction` | BaseAction | `openspec artifact` | Prompts for delivery mode |
| `GenerateAllArtifactsAction` | BaseAction | `openspec artifact --all` | Requires configured AI provider |
| `OpenSpecArchiveAction` | CliAction | `openspec archive` | Falls back to file move |
| `OpenSpecListAction` | CliAction | `openspec list` | Falls back to file parsing |
| `OpenSpecRefreshAction` | CliAction | *(none)* | Rebuilds tree model directly |
| `CreateDeltaSpecAction` | AnAction | *(none)* | Direct AnAction; creates file in change dir |

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
