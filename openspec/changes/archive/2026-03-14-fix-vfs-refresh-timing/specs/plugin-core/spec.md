## MODIFIED Requirements

### Requirement: VFS-Consistent File Operations

All plugin file write operations SHALL use IntelliJ's VirtualFile API within `WriteAction` to ensure the Virtual File System is immediately aware of changes. Direct `java.nio.file` writes (`Files.writeString`, `Files.createDirectories`) SHALL NOT be used for files within the project directory.

#### Scenario: Artifact generation writes are VFS-visible
- **WHEN** `ArtifactOrchestrationService` writes a generated artifact to disk
- **THEN** the file SHALL be written via VirtualFile API within `WriteAction`
- **AND** the file SHALL be immediately visible in the project tree without polling delay

#### Scenario: CLI-created files are VFS-visible before return
- **WHEN** `ScaffoldingService.initOpenSpec()` delegates to the CLI and the CLI creates files outside VFS
- **THEN** the service SHALL perform a synchronous VFS refresh before returning
- **AND** the `config.yaml` SHALL be findable via `LocalFileSystem.refreshAndFindFileByPath()` immediately after init returns

#### Scenario: Tool window refresh sees newly created changes
- **WHEN** `refreshToolWindow()` is called after a file write operation (e.g., propose)
- **THEN** the VFS refresh SHALL complete synchronously before the tool window tree is rebuilt
- **AND** `GettingStartedPanel.detectState()` SHALL see the new change directory
