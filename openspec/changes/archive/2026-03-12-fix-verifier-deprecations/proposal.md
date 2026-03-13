## Why

The Plugin Verifier reports 16+ deprecation warnings and 4 override-only violations against IntelliJ 2025.1 (261). Several of these APIs are **scheduled for removal**, meaning the plugin will break on future IDE versions if not migrated now. Fixing these before the v0.1.0 release ensures forward compatibility and a clean verifier report.

## What Changes

- Replace `ReadAction.compute()` with `ReadAction.computeCancellable()` in 5 files (BuiltInValidator, ConfigService, SpecParsingService, ChangeService, TrackingMetadataWriter)
- Replace deprecated `ProcessAdapter` with `ProcessListener` in CliRunner and CliDetectionService
- Replace `AnActionEvent.createFromAnAction()` (scheduled for removal) with `ActionManager.getInstance().tryToExecute()` or equivalent modern API in 3 tool window panels
- Replace `addBrowseFolderListener()` (scheduled for removal) with `installPathCompletion()` or modern equivalent in OpenSpecSettingsPanel
- Replace `Messages.showChooseDialog()` with popup-based selection in OpenSpecArchiveAction and OpenSpecApplyAction
- Replace `FileChooserDescriptorFactory.createSingleFileDescriptor()` (no-arg) with typed overload in OpenSpecSettingsPanel
- Replace `SlowOperations.allowSlowOperations()` (scheduled for removal) with proper background dispatch in OpenSpecToolWindowPanel
- Fix override-only `AnAction.actionPerformed()` direct calls by using ActionManager dispatch

## Capabilities

### New Capabilities
- `api-modernization`: Spec covering the requirement that the plugin uses only non-deprecated IntelliJ Platform APIs and passes Plugin Verifier without deprecation warnings or override-only violations

### Modified Capabilities
- `plugin-core`: Add requirement that ReadAction calls use cancellable variants for cooperative cancellation

## Impact

- 8 source files across services, tool windows, settings, actions, and utilities
- No user-facing behavior changes — all migrations are internal API replacements
- Must pass Plugin Verifier against IC-242 through IU-261 after migration
