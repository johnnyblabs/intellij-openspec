# EDT Compliance

## Purpose
Threading and lifecycle rules for IntelliJ plugin code: EDT discipline (no blocking I/O on the EDT, including credential-store access), background tasks, VFS refresh, honest cancellation of cancellable tasks, disposable lifetime for tool-window content, and error handling in `invokeLater` blocks.

## Requirements

### Requirement: Actions MUST NOT perform I/O on the EDT

All `AnAction.actionPerformed` implementations that invoke process execution, filesystem I/O, or network calls SHALL dispatch those operations to a background thread via `ProgressManager.getInstance().run(Task.Backgroundable)` or `ApplicationManager.getApplication().executeOnPooledThread()`. UI updates following the background work SHALL be posted back to the EDT via `ApplicationManager.getApplication().invokeLater()`.

#### Scenario: Init action offloads CLI execution
- **WHEN** `OpenSpecInitAction.actionPerformed` is invoked
- **THEN** the scaffolding and CLI calls SHALL execute on a background thread, and the success notification and tool window refresh SHALL execute via `invokeLater` on the EDT

#### Scenario: Propose action offloads file creation
- **WHEN** `OpenSpecProposeAction.actionPerformed` is invoked after the dialog returns
- **THEN** the `ScaffoldingService.createChange` call SHALL execute via `invokeLater` (since it requires WriteAction/EDT), and the notification, tool window refresh, and auto-focus SHALL chain inside the same EDT dispatch

### Requirement: Background tasks MUST NOT use invokeAndWait

Code running inside `Task.Backgroundable.run()` or `executeOnPooledThread` SHALL NOT call `ApplicationManager.getApplication().invokeAndWait()`. All EDT dispatch from background tasks SHALL use `invokeLater()` instead. Post-completion logic that depends on the EDT work SHALL be placed inside the `invokeLater` lambda or coordinated via `CountDownLatch`.

#### Scenario: invokeAndWait rejected in background task
- **WHEN** a background task needs to execute a `WriteAction` on the EDT
- **THEN** it SHALL use `invokeLater` with success/error handling inside the lambda, NOT `invokeAndWait`

#### Scenario: Sequential EDT work from background loop
- **WHEN** a background task iterates over items and each iteration requires EDT work
- **THEN** each iteration SHALL post its EDT work via `invokeLater` and coordinate completion via an atomic counter or `CountDownLatch`

### Requirement: VFS refresh threading

VFS index operations SHALL run on background threads when possible, and only the final file-open or `WriteAction` mutation SHALL run on the EDT. A background task SHALL NOT call a synchronous global VFS refresh. After a background step that writes files, it SHALL request an asynchronous refresh of the affected directory, and any UI update that depends on those files SHALL run on the EDT after that refresh completes. An error in the background step SHALL still restore the UI.

#### Scenario: VFS lookup before EDT hop
- **WHEN** a background thread writes a file and needs to open it in an editor
- **THEN** the `refreshAndFindFileByNioFile` call SHALL execute on the background thread, and only the `FileEditorManager.openFile` call SHALL execute via `invokeLater` on the EDT

#### Scenario: VFS refresh in WriteAction via invokeLater
- **WHEN** a background thread writes file content via `Files.writeString` and needs a VFS refresh
- **THEN** the VFS refresh inside `WriteAction` SHALL be posted via `invokeLater`, NOT `invokeAndWait`

#### Scenario: Refresh after a background CLI run
- **WHEN** a background task's CLI command succeeds and the UI must reflect files it wrote (for example, the Manage AI Tools list after adding a tool)
- **THEN** the task SHALL request an asynchronous refresh of the project directory, and the list reload, success notification, and control reset SHALL run on the EDT after that refresh completes, with no threading assertion raised

#### Scenario: Unexpected failure restores the UI
- **WHEN** a background task fails with any exception, not only an expected CLI error
- **THEN** the controls it disabled SHALL be re-enabled on the EDT and the failure SHALL be reported to the user

#### Scenario: Named VFS operations
- **WHEN** VFS index operations are made
- **THEN** they are `refreshAndFindFileByPath` and `refreshAndFindFileByNioFile`, run on background threads when possible

### Requirement: Error handling in invokeLater blocks

Every `invokeLater` lambda that performs operations which can throw (e.g., `WriteAction.run`, `archiveChange`) SHALL wrap the body in a `try/catch` block. Caught exceptions SHALL be reported to the user via `OpenSpecNotifier.error()`.

#### Scenario: Archive failure reported from invokeLater
- **WHEN** `changeService.archiveChange` throws an `IOException` inside an `invokeLater` block
- **THEN** the exception SHALL be caught and reported via `OpenSpecNotifier.error` with the change name and error message

#### Scenario: Scaffolding failure reported from background task
- **WHEN** `scaffolding.initOpenSpec` throws an exception during background execution
- **THEN** the exception SHALL be caught and reported via `OpenSpecNotifier.error` posted to the EDT via `invokeLater`

### Requirement: Credential store I/O off the EDT

`PasswordSafe` access SHALL NOT run on the EDT. A handler that stores, retrieves or checks an API key SHALL access it on a background thread and post the UI update back to the EDT with `invokeLater` and an explicit `ModalityState`. A key-exists check that gates UI SHALL use a cached boolean, not a synchronous keystore read.

#### Scenario: Provider selection and connection test do credential I/O off the EDT
- **WHEN** the user changes the Direct API provider dropdown or clicks Test in Settings
- **THEN** the `PasswordSafe` read/write SHALL run on a background thread and the field/label update SHALL be posted back via `invokeLater(..., ModalityState)`, so no blocking keystore access runs on the EDT

#### Scenario: Configuration check does not read the keystore on the EDT
- **WHEN** a UI path needs to know whether Direct API is configured (e.g. to show the delivery selector entry or gate Fast-Forward)
- **THEN** it SHALL read a cached has-key flag rather than performing a synchronous `PasswordSafe.get()` on the EDT

#### Scenario: Why access is off the EDT
- **WHEN** a `PasswordSafe` read or write is made
- **THEN** it is blocking (`@RequiresBackgroundThread`), so it SHALL run off the EDT

#### Scenario: Background access and modality
- **WHEN** a handler accesses the key store
- **THEN** it SHALL use `executeOnPooledThread` or an existing background worker
- **AND** the UI update SHALL be posted with `invokeLater` and an explicit `ModalityState` (for example `ModalityState.stateForComponent(...)`)

### Requirement: Disposable lifetime for tool-window content and panels

A tool-window `Content` whose component owns a `Disposable`, such as a console, alarm or message bus connection, SHALL have a disposer set with `Content.setDisposer(...)`. Its panel SHALL implement `Disposable` and release those resources in `dispose()`. Subscriptions and alarms SHALL be parented to a `Disposable` torn down with the content, and a transient check SHALL NOT register itself in the `Disposer`.

#### Scenario: Tool-window content is disposed on teardown
- **WHEN** the OpenSpec tool window's content is rebuilt or the project closes
- **THEN** the Browse and Console panels SHALL be disposed via their content disposer — releasing the `ConsoleView`, `Alarm`s, and VFS message-bus connection — with no "registered … but wasn't disposed" leak reported against the plugin

#### Scenario: Transient detection instance does not leak
- **WHEN** the plugin needs only to detect tool-window state (not display a panel)
- **THEN** it SHALL obtain that state without constructing a `Disposer`-registering panel that is then discarded

### Requirement: Honest cancellation of cancellable background tasks

A background task declared cancellable SHALL honor cancellation: it SHALL check `ProgressIndicator.isCanceled()` before applying its results (writing files, showing success UI), and any cancel affordance SHALL either actually abort the work or not be offered. `ProcessCanceledException` SHALL never be swallowed by a broad `catch` — it SHALL be rethrown so the platform can complete cancellation.

#### Scenario: Cancelling a single generation aborts before applying results
- **WHEN** the user cancels a single Direct API generation
- **THEN** the task SHALL NOT write the artifact file or show a "Generated" notification for the cancelled run

#### Scenario: ProcessCanceledException is not reported as a failure
- **WHEN** a `ProcessCanceledException` is raised inside a generation task's `try` block
- **THEN** it SHALL be rethrown rather than caught and surfaced as a "Generation failed" error
