## MODIFIED Requirements

### Requirement: VFS-consistent file operations

All file writes that create or update content SHALL use plain filesystem I/O (`java.nio.file`) on background threads. VFS refresh operations (`LocalFileSystem.refreshAndFindFileByPath`, `VirtualFile.refresh`) SHALL be performed within `WriteAction` on the EDT. CLI-created files SHALL be followed by synchronous VFS refresh. Tool window refresh SHALL use `syncRefresh()`.

#### Scenario: Files visible after write
- **WHEN** the plugin writes files (init, propose, generate)
- **THEN** they SHALL be immediately visible in the project tree without async delay

#### Scenario: File write does not require WriteAction
- **WHEN** the plugin writes file content via `Files.writeString()` or equivalent
- **THEN** the write SHALL execute outside of `WriteAction` and outside of `invokeAndWait`

#### Scenario: VFS refresh requires WriteAction
- **WHEN** the plugin needs to refresh the virtual file system after a file write
- **THEN** the VFS refresh SHALL execute within `WriteAction` on the EDT

## ADDED Requirements

### Requirement: CLI execution threading compliance

CLI commands executed via `CliRunner` and `CliDetectionService` SHALL use `Process` directly (via `GeneralCommandLine.createProcess()`) instead of `OSProcessHandler`. This SHALL allow CLI execution under any threading context, including ReadAction.

#### Scenario: CLI execution under ReadAction
- **WHEN** a CLI command is executed from a code path running under ReadAction (e.g., action update cycle)
- **THEN** the execution SHALL complete without throwing a threading violation

#### Scenario: CLI stream draining prevents deadlock
- **WHEN** `CliRunner` executes a CLI command that produces output on both stdout and stderr
- **THEN** both streams SHALL be drained concurrently to prevent buffer deadlock

#### Scenario: CLI detection stream handling
- **WHEN** `CliDetectionService` runs detection commands (version check, which, PATH resolution)
- **THEN** stdout SHALL be fully read before waiting for process exit

### Requirement: Cross-thread field visibility

Fields in UI components that are written on background threads and read on the EDT (or vice versa) SHALL be declared `volatile` to ensure cross-thread visibility under the Java Memory Model.

#### Scenario: WorkflowActionPanel field visibility
- **WHEN** a pooled thread updates `activeChangeName`, `nextArtifactId`, `lastPrompt`, `lastOutputPath`, or `hasDeltaSpecs`
- **THEN** the updated value SHALL be visible to the EDT on its next read without requiring explicit synchronization

#### Scenario: UI state captured on EDT before dispatch
- **WHEN** a UI action dispatches work to a background thread that depends on Swing component state
- **THEN** the component state SHALL be read on the EDT before the background dispatch