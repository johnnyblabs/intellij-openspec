## ADDED Requirements

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

VFS index operations (`refreshAndFindFileByPath`, `refreshAndFindFileByNioFile`) SHALL be performed on background threads when possible. Only the final file-open or `WriteAction` VFS mutation SHALL execute on the EDT.

#### Scenario: VFS lookup before EDT hop
- **WHEN** a background thread writes a file and needs to open it in an editor
- **THEN** the `refreshAndFindFileByNioFile` call SHALL execute on the background thread, and only the `FileEditorManager.openFile` call SHALL execute via `invokeLater` on the EDT

#### Scenario: VFS refresh in WriteAction via invokeLater
- **WHEN** a background thread writes file content via `Files.writeString` and needs a VFS refresh
- **THEN** the VFS refresh inside `WriteAction` SHALL be posted via `invokeLater`, NOT `invokeAndWait`

### Requirement: Error handling in invokeLater blocks

Every `invokeLater` lambda that performs operations which can throw (e.g., `WriteAction.run`, `archiveChange`) SHALL wrap the body in a `try/catch` block. Caught exceptions SHALL be reported to the user via `OpenSpecNotifier.error()`.

#### Scenario: Archive failure reported from invokeLater
- **WHEN** `changeService.archiveChange` throws an `IOException` inside an `invokeLater` block
- **THEN** the exception SHALL be caught and reported via `OpenSpecNotifier.error` with the change name and error message

#### Scenario: Scaffolding failure reported from background task
- **WHEN** `scaffolding.initOpenSpec` throws an exception during background execution
- **THEN** the exception SHALL be caught and reported via `OpenSpecNotifier.error` posted to the EDT via `invokeLater`
