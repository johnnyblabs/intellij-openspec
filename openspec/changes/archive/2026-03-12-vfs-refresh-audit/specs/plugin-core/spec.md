## ADDED Requirements

### Requirement: VFS-Consistent File Operations

All plugin file write operations SHALL use IntelliJ's VirtualFile API within `WriteAction` to ensure the Virtual File System is immediately aware of changes. Direct `java.nio.file` writes (`Files.writeString`, `Files.createDirectories`) SHALL NOT be used for files within the project directory.

#### Scenario: Artifact generation writes are VFS-visible
- **WHEN** `ArtifactOrchestrationService` writes a generated artifact to disk
- **THEN** the file SHALL be written via VirtualFile API within `WriteAction`
- **AND** the file SHALL be immediately visible in the project tree without polling delay

#### Scenario: Tracking metadata writes are VFS-visible
- **WHEN** `TrackingMetadataWriter` updates `.openspec.yaml` tracking metadata
- **THEN** the file SHALL be written via VirtualFile API within `WriteAction`
- **AND** VFS change listeners SHALL fire immediately after the write

#### Scenario: Tracking metadata reads use VFS
- **WHEN** `TrackingMetadataWriter` reads `.openspec.yaml` content
- **THEN** it SHALL read via VirtualFile API to ensure consistency with pending VFS writes

#### Scenario: Parent directory creation uses VFS
- **WHEN** an artifact output path requires new parent directories
- **THEN** the directories SHALL be created via `VfsUtil.createDirectoryIfMissing()` within `WriteAction`
