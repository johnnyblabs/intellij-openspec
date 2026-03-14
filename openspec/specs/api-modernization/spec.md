# API Modernization

## Purpose
Ensure the plugin uses only non-deprecated IntelliJ Platform APIs and passes Plugin Verifier without deprecation warnings or override-only violations.

## Requirements

### Requirement: No scheduled-for-removal API usage

The plugin SHALL NOT use any IntelliJ Platform API marked as scheduled for removal.

#### Scenario: AnActionEvent creation uses modern API
- **WHEN** plugin code needs to programmatically invoke an AnAction
- **THEN** it SHALL use `ActionUtil.invokeAction()` instead of `AnActionEvent.createFromAnAction()` followed by `action.actionPerformed()`

#### Scenario: Browse folder listeners use non-deprecated overload
- **WHEN** a settings panel attaches a file browser to a TextFieldWithBrowseButton
- **THEN** it SHALL use `addBrowseFolderListener(TextBrowseFolderListener)` instead of the deprecated multi-parameter overload

#### Scenario: Slow operations are avoided instead of suppressed
- **WHEN** plugin code performs a potentially slow EDT operation
- **THEN** it SHALL schedule the work appropriately (invokeLater, background task) instead of wrapping in `SlowOperations.allowSlowOperations()`

### Requirement: No deprecated API usage

The plugin SHALL replace deprecated IntelliJ Platform APIs with their recommended modern equivalents.

#### Scenario: Process listeners use ProcessListener interface
- **WHEN** plugin code listens to process output from CLI commands
- **THEN** it SHALL implement `ProcessListener` directly instead of extending `ProcessAdapter`

#### Scenario: Choice dialogs use popup-based selection
- **WHEN** plugin code presents a list of choices to the user
- **THEN** it SHALL use `JBPopupFactory` popup chooser instead of `Messages.showChooseDialog()`

#### Scenario: File chooser descriptors use typed factory methods
- **WHEN** plugin code creates a file chooser descriptor
- **THEN** it SHALL use a typed factory method instead of the deprecated no-arg `createSingleFileDescriptor()`

#### Scenario: Programmatic action invocation uses invokeAction
- **WHEN** plugin code needs to programmatically invoke an AnAction from a UI panel
- **THEN** it SHALL use `ActionUtil.invokeAction(action, dataContext, place, inputEvent)` instead of constructing an AnActionEvent and calling `performActionDumbAwareWithCallbacks()`

### Requirement: No override-only API violations

The plugin SHALL NOT directly call methods that are designated as override-only in the IntelliJ Platform SDK.

#### Scenario: Actions invoked via ActionUtil not direct call
- **WHEN** plugin code invokes an action programmatically
- **THEN** it SHALL use `ActionUtil.invokeAction()` or `ActionManager` dispatch instead of calling `action.actionPerformed()` directly

### Requirement: Plugin Verifier passes cleanly

The plugin SHALL pass Plugin Verifier across all supported IDE versions without deprecation warnings or override-only violations introduced by plugin code.

#### Scenario: Clean verifier report after migration
- **WHEN** `runPluginVerifier` executes against IDE versions IC-242 through IU-261
- **THEN** the report SHALL contain zero deprecation warnings and zero override-only violations for plugin code
