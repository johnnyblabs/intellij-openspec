## ADDED Requirements

### Requirement: No deprecated action invocation API usage

The plugin SHALL NOT use `ActionUtil.performActionDumbAwareWithCallbacks()` to invoke actions programmatically.

#### Scenario: Programmatic action invocation uses invokeAction
- **WHEN** plugin code needs to programmatically invoke an AnAction from a UI panel
- **THEN** it SHALL use `ActionUtil.invokeAction(action, dataContext, place, inputEvent)` instead of constructing an AnActionEvent and calling `performActionDumbAwareWithCallbacks()`
