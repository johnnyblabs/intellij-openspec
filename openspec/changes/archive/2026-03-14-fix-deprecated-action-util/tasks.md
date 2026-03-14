## 1. Replace deprecated performActionDumbAwareWithCallbacks calls

- [x] 1.1 Replace `performActionDumbAwareWithCallbacks` with `ActionUtil.invokeAction()` in `GettingStartedPanel.createProposeButton()` — remove manual AnActionEvent construction
- [x] 1.2 Replace `performActionDumbAwareWithCallbacks` with `ActionUtil.invokeAction()` in `OpenSpecToolWindowPanel.handleHintAction()` — remove manual AnActionEvent construction
- [x] 1.3 Replace `performActionDumbAwareWithCallbacks` with `ActionUtil.invokeAction()` in `WorkflowActionPanel.updatePipelineAndButton()` (propose link lambda) — remove manual AnActionEvent construction
- [x] 1.4 Replace `performActionDumbAwareWithCallbacks` with `ActionUtil.invokeAction()` in `WorkflowActionPanel.onStartNewChange()` — remove manual AnActionEvent construction

## 2. Verify

- [x] 2.1 Build plugin and confirm zero compilation errors
- [x] 2.2 Run Plugin Verifier and confirm `performActionDumbAwareWithCallbacks` no longer appears in deprecated-usages report
