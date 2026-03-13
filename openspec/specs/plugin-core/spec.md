# Plugin Core

## Purpose
Core plugin infrastructure: project detection, configuration parsing, and service lifecycle.

## Requirements

### Requirement: Project Detection

The plugin SHALL detect whether a project contains an OpenSpec configuration by checking for the presence of an `openspec/` directory at the project root.

#### Scenario: Project with OpenSpec directory
- GIVEN a project with an `openspec/` directory containing `config.yaml`
- WHEN the project is opened in the IDE
- THEN the plugin SHALL recognize it as an OpenSpec project
- AND enable all OpenSpec features

#### Scenario: Project without OpenSpec directory
- GIVEN a project without an `openspec/` directory
- WHEN the project is opened in the IDE
- THEN the plugin SHALL NOT enable OpenSpec features
- AND the OpenSpec tool window SHOULD be hidden

### Requirement: Config Parsing

The plugin SHALL parse the `openspec/config.yaml` file and make its contents available to all plugin services. When parsing fails, the plugin SHALL show a clear error notification.

#### Scenario: Valid config file
- GIVEN a valid `openspec/config.yaml` with schema, profile, and rules
- WHEN the config service initializes
- THEN all config values SHALL be accessible via the ConfigService API

#### Scenario: Missing config file
- GIVEN an `openspec/` directory without a `config.yaml` file
- WHEN the config service initializes
- THEN the service SHALL report an error
- AND the plugin SHOULD notify the user

#### Scenario: Malformed config YAML
- **WHEN** the config service loads a `config.yaml` that contains invalid YAML syntax
- **THEN** the service SHALL show a warning notification with the file path, line number, and parse error description
- **AND** the error SHALL be logged at WARN level
- **AND** the config SHALL be treated as missing (null)

### Requirement: Change metadata parse error reporting

The plugin SHALL show a warning notification when `.openspec.yaml` contains invalid YAML, rather than silently skipping the metadata.

#### Scenario: Malformed change metadata
- **WHEN** ChangeService loads a `.openspec.yaml` that contains invalid YAML syntax
- **THEN** the service SHALL show a warning notification with the file path and parse error description
- **AND** the change SHALL still appear in the tree (without metadata)

### Requirement: Proposal Scaffolding Template

The plugin's built-in proposal template SHALL match the official OpenSpec 1.2.0 spec-driven schema template structure.

#### Scenario: Generated proposal structure
- WHEN the plugin scaffolds a new proposal.md via built-in scaffolding
- THEN the generated file SHALL contain the sections `## Why`, `## What Changes`, `## Capabilities` (with `### New Capabilities` and `### Modified Capabilities`), and `## Impact`
- THEN the generated file SHALL NOT contain a `# Proposal:` H1 heading, `## Summary`, or `## Motivation` section

#### Scenario: User input placed in corresponding sections
- WHEN the user provides text in the "Why" dialog field during proposal creation
- THEN that text SHALL appear under the `## Why` section in the generated proposal.md
- WHEN the user provides text in the "What Changes" dialog field during proposal creation
- THEN that text SHALL appear under the `## What Changes` section in the generated proposal.md

#### Scenario: Blank optional fields use placeholders
- WHEN the user leaves the "Why" or "What Changes" fields blank during proposal creation
- THEN the generated proposal.md SHALL contain the standard HTML comment placeholders for those sections

### Requirement: Spec File Discovery

The plugin SHALL discover all spec files under the `openspec/specs/` directory tree.

#### Scenario: Multiple spec domains
- GIVEN an `openspec/specs/` directory with subdirectories for each domain
- WHEN the spec parsing service scans for specs
- THEN it SHALL return a SpecFile for each `spec.md` found
- AND each SpecFile SHALL include its domain name derived from the parent directory

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

### Requirement: ReadAction-protected VirtualFile reads

All plugin VirtualFile read operations SHALL be wrapped in `ReadAction.compute()` when executing on background threads. This ensures the IntelliJ read lock is held during VFS access, preventing stale reads and assertion errors during concurrent write actions.

#### Scenario: Spec parsing reads use ReadAction
- **WHEN** `SpecParsingService.parseSpec()` reads a VirtualFile's content on a background thread
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.compute()`

#### Scenario: Change metadata reads use ReadAction
- **WHEN** `ChangeService.getChangesFromDir()` reads `.openspec.yaml` via `getInputStream()` on a background thread
- **THEN** the stream read SHALL be wrapped in `ReadAction.compute()`

#### Scenario: Config reload reads use ReadAction
- **WHEN** `ConfigService.reload()` reads `config.yaml` via `getInputStream()` on a background thread
- **THEN** the stream read SHALL be wrapped in `ReadAction.compute()`

#### Scenario: Tracking metadata reads use ReadAction
- **WHEN** `TrackingMetadataWriter.readYaml()` reads a VirtualFile's content
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.compute()`

#### Scenario: Validation file reads use ReadAction
- **WHEN** `BuiltInValidator` reads file content for validation on a background thread
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.compute()`

#### Scenario: ReadAction fallback in test context
- **WHEN** a VirtualFile read executes without an IntelliJ Application (unit test context)
- **THEN** the code SHALL fall back to direct NIO file access without `ReadAction`

### Requirement: Thread-safe ConfigService initialization

The `ConfigService` SHALL use volatile field access and synchronized initialization to prevent race conditions when multiple threads call `getConfig()` concurrently.

#### Scenario: Concurrent getConfig calls
- **WHEN** multiple threads call `ConfigService.getConfig()` simultaneously and `config` is null
- **THEN** exactly one thread SHALL execute `reload()`
- **AND** other threads SHALL wait for and receive the loaded config

#### Scenario: Fast path after initialization
- **WHEN** `getConfig()` is called after config has been loaded
- **THEN** the method SHALL return the cached config without acquiring any lock

### Requirement: Post-archive convention in config rules

The project's `openspec/config.yaml` SHALL include a `post-archive` rule declaring the post-archive workflow steps as a project convention visible to all AI tools.

#### Scenario: Config contains post-archive rule
- **WHEN** any AI tool reads the project's config.yaml rules
- **THEN** a `post-archive` rule SHALL be present describing the commit, push, and tracker update steps
