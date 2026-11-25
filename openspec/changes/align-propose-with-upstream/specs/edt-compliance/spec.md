## MODIFIED Requirements

### Requirement: Actions MUST NOT perform I/O on the EDT

All `AnAction.actionPerformed` implementations that invoke process execution, filesystem I/O, or network calls SHALL dispatch those operations to a background thread via `ProgressManager.getInstance().run(Task.Backgroundable)` or `ApplicationManager.getApplication().executeOnPooledThread()`. UI updates following the background work SHALL be posted back to the EDT via `ApplicationManager.getApplication().invokeLater()`.

#### Scenario: Init action offloads CLI execution
- **WHEN** `OpenSpecInitAction.actionPerformed` is invoked
- **THEN** the scaffolding and CLI calls SHALL execute on a background thread, and the success notification and tool window refresh SHALL execute via `invokeLater` on the EDT

#### Scenario: Propose action offloads file creation
- **WHEN** Propose or New Change creates a change after its dialog returns
- **THEN** the CLI's `new change` SHALL run on a background thread, a scaffold written without the CLI SHALL run in a write action, and the notification, tool window refresh and change selection SHALL run on the EDT after the change exists
