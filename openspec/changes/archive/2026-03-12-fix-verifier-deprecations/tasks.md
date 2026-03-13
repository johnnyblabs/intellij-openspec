## 1. ReadAction.compute → ReadAction.computeCancellable

- [x] 1.1 Replace `ReadAction.compute()` with `ReadAction.computeCancellable()` in `BuiltInValidator.readFile()`
- [x] 1.2 Replace `ReadAction.compute()` with `ReadAction.computeCancellable()` in `ConfigService.reload()`
- [x] 1.3 Replace `ReadAction.compute()` with `ReadAction.computeCancellable()` in `SpecParsingService.parseSpec()`
- [x] 1.4 Replace `ReadAction.compute()` with `ReadAction.computeCancellable()` in `ChangeService.getChangesFromDir()`
- [x] 1.5 Replace `ReadAction.compute()` with `ReadAction.computeCancellable()` in `TrackingMetadataWriter.readYaml()`

## 2. ProcessAdapter → ProcessListener

- [x] 2.1 Replace `ProcessAdapter` with `ProcessListener` in `CliRunner`
- [x] 2.2 Replace all `ProcessAdapter` usages with `ProcessListener` in `CliDetectionService` (3 instances)

## 3. AnActionEvent.createFromAnAction + actionPerformed → ActionUtil.performActionDumbAwareWithCallbacks

- [x] 3.1 Replace `createFromAnAction()` + `actionPerformed()` with constructor + `performActionDumbAwareWithCallbacks()` in `GettingStartedPanel`
- [x] 3.2 Replace `createFromAnAction()` + `actionPerformed()` with constructor + `performActionDumbAwareWithCallbacks()` in `OpenSpecToolWindowPanel`
- [x] 3.3 Replace `createFromAnAction()` + `actionPerformed()` with constructor + `performActionDumbAwareWithCallbacks()` in `WorkflowActionPanel`

## 4. Settings panel deprecations

- [x] 4.1 Replace `addBrowseFolderListener()` deprecated overload with `TextBrowseFolderListener` in `OpenSpecSettingsPanel`
- [x] 4.2 Replace `FileChooserDescriptorFactory.createSingleFileDescriptor()` no-arg with `createSingleFileNoJarsDescriptor()` in `OpenSpecSettingsPanel`

## 5. Messages.showChooseDialog → JBPopupFactory

- [x] 5.1 Replace `Messages.showChooseDialog()` with `JBPopupFactory` popup in `OpenSpecApplyAction`
- [x] 5.2 Replace `Messages.showChooseDialog()` with `JBPopupFactory` popup in `OpenSpecArchiveAction`

## 6. SlowOperations.allowSlowOperations → proper scheduling

- [x] 6.1 Replace `SlowOperations.allowSlowOperations()` with `invokeLater()` in `OpenSpecToolWindowPanel`

## 7. Verification

- [x] 7.1 Run `./gradlew test` to confirm all existing tests pass
- [x] 7.2 Run `./gradlew verifyPlugin` and confirm deprecation/override-only violations are resolved
