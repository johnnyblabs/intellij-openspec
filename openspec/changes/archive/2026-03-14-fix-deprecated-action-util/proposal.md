## Why

The Plugin Verifier reports 4 remaining usages of the deprecated `ActionUtil.performActionDumbAwareWithCallbacks()` method across 3 UI panels. These were introduced during the previous deprecation fix round (v0.2.0) as replacements for direct `actionPerformed()` calls, but `performActionDumbAwareWithCallbacks` is itself deprecated and scheduled for removal. Fixing these now ensures a clean verifier report for v0.2.1 and avoids breakage in future platform versions.

## What Changes

- Replace all 4 calls to `ActionUtil.performActionDumbAwareWithCallbacks(action, event)` with `ActionUtil.invokeAction(action, dataContext, place, inputEvent)`
- Remove manual `AnActionEvent` construction at each call site (the new API handles this internally)
- Affected call sites:
  - `GettingStartedPanel.lambda$createProposeButton$4(...)`
  - `OpenSpecToolWindowPanel.handleHintAction(...)`
  - `WorkflowActionPanel.lambda$updatePipelineAndButton$17(...)`
  - `WorkflowActionPanel.onStartNewChange()`

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

- `api-modernization`: Adding scenario for `performActionDumbAwareWithCallbacks` → `invokeAction` migration under the existing "No deprecated API usage" requirement.

## Impact

- 3 source files modified: `GettingStartedPanel.java`, `OpenSpecToolWindowPanel.java`, `WorkflowActionPanel.java`
- No API or behavior changes — actions are invoked identically, just through the modern entry point
- `invokeAction()` additionally calls `update()` before performing the action, which is a correctness improvement
