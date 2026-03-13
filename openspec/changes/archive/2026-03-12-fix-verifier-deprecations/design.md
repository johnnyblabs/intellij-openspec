## Context

The Plugin Verifier (run against IC-242 through IU-261) reports deprecation warnings and scheduled-for-removal violations. These fall into 3 tiers:

- **Scheduled for removal** (will break): `AnActionEvent.createFromAnAction()`, `addBrowseFolderListener()`, `SlowOperations.allowSlowOperations()`
- **Deprecated** (should fix): `ReadAction.compute()`, `ProcessAdapter`, `Messages.showChooseDialog()`, `FileChooserDescriptorFactory.createSingleFileDescriptor()`
- **Override-only violations** (incorrect usage): `AnAction.actionPerformed()` called directly instead of via ActionManager

## Goals / Non-Goals

**Goals:**
- Eliminate all scheduled-for-removal API usages
- Replace deprecated APIs with modern equivalents
- Fix override-only violations
- Pass Plugin Verifier cleanly across IC-242 through IU-261
- Maintain identical user-facing behavior

**Non-Goals:**
- Refactoring beyond the minimal API replacement
- Adding new features or changing UX
- Dropping support for IDE versions before 242

## Decisions

### ReadAction.compute() → ReadAction.computeCancellable()

Replace in all 5 files. `computeCancellable()` supports cooperative cancellation — if a background read is cancelled, it throws `ProcessCanceledException` instead of blocking. The lambdas already throw checked exceptions, so the signature change is minimal. Keep the `ApplicationManager.getApplication() == null` fallback for test context.

**Files:** BuiltInValidator, ConfigService, SpecParsingService, ChangeService, TrackingMetadataWriter

### ProcessAdapter → ProcessListener

Replace `new ProcessAdapter()` with `new ProcessListener()` implementing only the needed methods. Both CliRunner and CliDetectionService only override `onTextAvailable()` and `processTerminated()`, so the migration is a direct interface swap.

**Files:** CliRunner, CliDetectionService

### AnActionEvent.createFromAnAction() + action.actionPerformed() → ActionUtil.invokeAction()

This fixes both the scheduled-for-removal `createFromAnAction()` and the override-only `actionPerformed()` violation in one shot. Use `ActionUtil.invokeAction(action, dataContext, place, inputEvent)` which handles event creation, dumb-awareness, and callbacks internally.

**Files:** GettingStartedPanel, OpenSpecToolWindowPanel, WorkflowActionPanel

### addBrowseFolderListener() → addBrowseFolderListener(TextBrowseFolderListener)

The deprecated overload takes individual string parameters. Replace with the non-deprecated overload that takes a `TextBrowseFolderListener` object. Also fix `FileChooserDescriptorFactory.createSingleFileDescriptor()` (no-arg deprecated) by using `createSingleFileNoJarsDescriptor()` or the typed variant.

**Files:** OpenSpecSettingsPanel

### Messages.showChooseDialog() → JBPopupFactory list popup

Replace modal dialog with a `JBPopupFactory.getInstance().createPopupChooserBuilder()` popup. This is both non-deprecated and better UX (non-modal, searchable). The callback-based API requires restructuring the action methods slightly.

**Files:** OpenSpecApplyAction, OpenSpecArchiveAction

### SlowOperations.allowSlowOperations() → invokeLater

The current usage wraps `FileEditorManager.openFile()` which must run on EDT. The `SlowOperations` wrapper was papering over a thread assertion. Replace with `ApplicationManager.getApplication().invokeLater()` which properly schedules on EDT without the slow-operations assertion.

**Files:** OpenSpecToolWindowPanel

## Risks / Trade-offs

- **API availability across IDE versions**: Some replacement APIs may not exist in 242. Each migration will be verified against the minimum platform version. If a replacement isn't available in 242, we'll use conditional logic or keep the older API for that case.
- **ActionUtil.invokeAction() behavioral differences**: The ActionUtil path includes dumb-mode checks and update() calls that direct actionPerformed() skips. This is actually correct behavior — our current code skips the update() check, which could cause actions to fire when they shouldn't.
- **Popup vs dialog UX change**: The Messages.showChooseDialog → popup migration changes the selection UX from modal dialog to dropdown popup. This is a minor UX improvement (searchable, non-modal) but is a visible change.
