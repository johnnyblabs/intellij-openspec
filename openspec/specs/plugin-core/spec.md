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

#### Scenario: Init delegates to CLI when available
- **WHEN** `ScaffoldingService.initOpenSpec()` is called and the OpenSpec CLI is detected
- **THEN** the service SHALL run `openspec init --tools <detected-tools>` via `CliRunner`
- **AND** refresh the VFS after the CLI completes
- **AND** the generated files (skills, commands, config) SHALL be immediately visible in the project tree

#### Scenario: Init falls back to built-in when CLI unavailable
- **WHEN** `ScaffoldingService.initOpenSpec()` is called and the OpenSpec CLI is NOT detected
- **THEN** the service SHALL create the directory structure via built-in VFS operations
- **AND** the archive directory SHALL be created at `openspec/changes/archive/` (not `openspec/archive/`)

#### Scenario: Init falls back to built-in when CLI fails
- **WHEN** `ScaffoldingService.initOpenSpec()` delegates to the CLI and the CLI command fails
- **THEN** the service SHALL fall back to built-in directory creation
- **AND** log the CLI error at WARN level

### Requirement: AI tool detection covers all CLI-supported tools

The plugin SHALL detect all AI tools that the OpenSpec CLI supports, using the same directory-to-tool mappings.

#### Scenario: All 24 CLI tools are detectable
- **WHEN** `AiToolDetectionService.detect()` scans the project root
- **THEN** it SHALL check for all 24 tool directories defined by the CLI (`.amazonq`, `.agent`, `.augment`, `.claude`, `.cline`, `.codex`, `.codebuddy`, `.continue`, `.cospec`, `.crush`, `.cursor`, `.factory`, `.gemini`, `.github`, `.iflow`, `.kilocode`, `.kiro`, `.opencode`, `.pi`, `.qoder`, `.qwen`, `.roo`, `.trae`, `.windsurf`)

#### Scenario: Each detected tool has a type classification
- **WHEN** a new AI tool is detected by directory scan
- **THEN** it SHALL have a `ToolType` classification (CLI or IDE_PANEL) matching the CLI's categorization

### Requirement: Tool name to CLI ID mapping

The `AiToolDetectionService` SHALL provide a mapping from display names to CLI tool IDs for use with `openspec init --tools`.

#### Scenario: Display name maps to CLI ID
- **WHEN** the plugin needs to pass detected tools to the CLI
- **THEN** each display name (e.g., "GitHub Copilot") SHALL map to its CLI ID (e.g., "github-copilot")

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

#### Scenario: CLI-created files are VFS-visible before return
- **WHEN** `ScaffoldingService.initOpenSpec()` delegates to the CLI and the CLI creates files outside VFS
- **THEN** the service SHALL perform a synchronous VFS refresh before returning
- **AND** the `config.yaml` SHALL be findable via `LocalFileSystem.refreshAndFindFileByPath()` immediately after init returns

#### Scenario: Tool window refresh sees newly created changes
- **WHEN** `refreshToolWindow()` is called after a file write operation (e.g., propose)
- **THEN** the VFS refresh SHALL complete synchronously before the tool window tree is rebuilt
- **AND** `GettingStartedPanel.detectState()` SHALL see the new change directory

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

All plugin VirtualFile read operations SHALL be wrapped in `ReadAction.computeCancellable()` when executing on background threads. This ensures the IntelliJ read lock is held during VFS access and supports cooperative cancellation.

#### Scenario: Spec parsing reads use cancellable ReadAction
- **WHEN** `SpecParsingService.parseSpec()` reads a VirtualFile's content on a background thread
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.computeCancellable()`

#### Scenario: Change metadata reads use cancellable ReadAction
- **WHEN** `ChangeService.getChangesFromDir()` reads `.openspec.yaml` via `getInputStream()` on a background thread
- **THEN** the stream read SHALL be wrapped in `ReadAction.computeCancellable()`

#### Scenario: Config reload reads use cancellable ReadAction
- **WHEN** `ConfigService.reload()` reads `config.yaml` via `getInputStream()` on a background thread
- **THEN** the stream read SHALL be wrapped in `ReadAction.computeCancellable()`

#### Scenario: Tracking metadata reads use cancellable ReadAction
- **WHEN** `TrackingMetadataWriter.readYaml()` reads a VirtualFile's content
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.computeCancellable()`

#### Scenario: Validation file reads use cancellable ReadAction
- **WHEN** `BuiltInValidator` reads file content for validation on a background thread
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.computeCancellable()`

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

### Requirement: Dependency versions remain latest stable compatible

The plugin core SHALL use the latest stable dependency versions that remain compatible with Java 21 and the supported IntelliJ platform baseline.

#### Scenario: Selecting target dependency versions
- **WHEN** maintainers prepare dependency upgrades
- **THEN** selected versions SHALL be the newest stable releases that are compatible with Java 21 and IntelliJ IDEA 2024.2+

#### Scenario: Rejecting incompatible versions
- **WHEN** a newer dependency version is incompatible with required Java or IntelliJ baseline constraints
- **THEN** the plugin SHALL keep the latest compatible stable version instead of adopting the incompatible version

### Requirement: Dependency upgrades include required code migration

Dependency upgrades SHALL include any required production code migration before the change is complete.

#### Scenario: API migration required by dependency upgrade
- **WHEN** a dependency update changes APIs used by plugin code
- **THEN** the implementation SHALL migrate affected code paths within the same change

#### Scenario: Runtime behavior migration required by dependency upgrade
- **WHEN** a dependency update changes runtime behavior that affects plugin outcomes
- **THEN** the implementation SHALL update plugin behavior and validation logic to preserve expected workflow results

### Requirement: File type extension declarations

The plugin's `plugin.xml` SHALL declare file type extensions for OpenSpec-specific file types and an `IconProvider` for path-based icon resolution.

#### Scenario: plugin.xml registers .openspec.yaml file type
- **WHEN** the plugin loads in the IDE
- **THEN** `plugin.xml` SHALL contain a `<fileType>` extension mapping `.openspec.yaml` to the custom OpenSpec YAML file type

#### Scenario: plugin.xml registers IconProvider
- **WHEN** the plugin loads in the IDE
- **THEN** `plugin.xml` SHALL contain an `<iconProvider>` extension for OpenSpec spec file icon resolution
