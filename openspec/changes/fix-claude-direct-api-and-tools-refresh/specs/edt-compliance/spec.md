# Spec Delta

## MODIFIED Requirements

### Requirement: VFS refresh threading

VFS index operations (`refreshAndFindFileByPath`, `refreshAndFindFileByNioFile`) SHALL be performed on background threads when possible. Only the final file-open or `WriteAction` VFS mutation SHALL execute on the EDT.

A background task SHALL NOT call a synchronous, global VFS refresh, because that requires write-intent access and fails off the EDT. To refresh after a background step (such as a CLI run that writes files), it SHALL request an **asynchronous** refresh of the affected directory. Any UI update that depends on the refreshed files SHALL run on the EDT **after** that refresh completes. An error in the background step SHALL still restore the UI (re-enable controls, report the error).

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
