## 1. WorkflowProfileService

- [x] 1.1 Create `WorkflowProfileService` (project-level service) with a `Set<String>` cache of active workflows, an `isWorkflowEnabled(String workflowId)` method, and a `refresh()` method
- [x] 1.2 Implement workflow resolution: run `openspec config list --json` via `CliRunner`, parse the `workflows` array, populate the set. Fall back to core defaults (`propose, explore, apply, archive`) if CLI unavailable or parse fails
- [x] 1.3 Register `WorkflowProfileService` in `plugin.xml` as a project service

## 2. Base Action Hook

- [x] 2.1 Add `protected String getWorkflowId()` to `OpenSpecBaseAction` returning `null` by default
- [x] 2.2 Modify `OpenSpecBaseAction.update()` to check `getWorkflowId()` against `WorkflowProfileService.isWorkflowEnabled()` — if workflow not enabled, set `enabled=false` and set the description to the guidance tooltip text

## 3. Workflow ID Overrides

- [x] 3.1 Override `getWorkflowId()` in `OpenSpecProposeAction` → `"propose"`
- [x] 3.2 Override `getWorkflowId()` in `ExploreContextAction` → `"explore"`
- [x] 3.3 Override `getWorkflowId()` in `OpenSpecApplyAction` → `"apply"`
- [x] 3.4 Override `getWorkflowId()` in `OpenSpecArchiveAction` → `"archive"`
- [x] 3.5 Override `getWorkflowId()` in `OpenSpecFfAction` → `"ff"`
- [x] 3.6 Override `getWorkflowId()` in `OpenSpecContinueAction` → `"continue"`
- [x] 3.7 Override `getWorkflowId()` in `OpenSpecVerifyAction` → `"verify"`
- [x] 3.8 Override `getWorkflowId()` in `OpenSpecSyncAction` → `"sync"`
- [x] 3.9 Override `getWorkflowId()` in `OpenSpecBulkArchiveAction` → `"bulk-archive"`

## 4. Settings Integration

- [x] 4.1 Call `WorkflowProfileService.refresh()` from `OpenSpecConfigurable.applyProfileChange()` after a successful profile switch

## 5. Verification

- [x] 5.1 Build compiles and all existing tests pass
- [x] 5.2 Verify utility actions (Init, Validate, List, Refresh, Update, ManageTools, SetupWizard) remain always enabled — they return null workflow ID
