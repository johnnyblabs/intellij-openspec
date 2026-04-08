# Plugin Core

## Purpose
Core infrastructure: project detection, configuration, file operations, API compatibility, notifications, and service lifecycle.

## Requirements

### Requirement: Project detection and initialization

The plugin SHALL detect OpenSpec projects by checking for `openspec/` and provide initialization via CLI delegation with built-in fallback.

#### Scenario: Project detection
- **WHEN** a project is opened in the IDE
- **THEN** the plugin SHALL check for `openspec/` directory and enable/disable features accordingly

#### Scenario: Init delegates to CLI when available
- **WHEN** `ScaffoldingService.initOpenSpec()` is called and the CLI is detected
- **THEN** it SHALL run `openspec init --tools <detected-tools>` and perform a synchronous VFS refresh

#### Scenario: Init falls back to built-in
- **WHEN** the CLI is unavailable or fails
- **THEN** the service SHALL create `openspec/config.yaml`, `specs/`, `changes/`, and `changes/archive/` via VFS

### Requirement: Configuration parsing

The plugin SHALL parse `openspec/config.yaml` and surface clear errors for malformed YAML in both `config.yaml` and `.openspec.yaml`. The plugin SHALL support reading the active profile's workflow configuration when the CLI is available. The plugin SHALL persist a default schema preference in `OpenSpecSettings.State` and expose it via getter/setter methods.

#### Scenario: Valid config
- **WHEN** a valid `config.yaml` exists
- **THEN** all config values SHALL be accessible via ConfigService

#### Scenario: Malformed YAML
- **WHEN** `config.yaml` or `.openspec.yaml` contains invalid YAML
- **THEN** the plugin SHALL show a warning notification with file path and parse error

#### Scenario: Profile config retrieval via CLI
- **WHEN** the CLI is detected and `openspec config profile --json` succeeds
- **THEN** the plugin SHALL make profile name, description, and active workflows available to the Settings panel

#### Scenario: Profile config retrieval without CLI
- **WHEN** the CLI is not detected
- **THEN** the plugin SHALL fall back to the locally-stored profile name in OpenSpecSettings without workflow details

#### Scenario: Default schema persistence
- **WHEN** the user selects a default schema in Settings
- **THEN** the value SHALL be persisted in `OpenSpecSettings.State.defaultSchema` and available via `OpenSpecSettings.getDefaultSchema()`

### Requirement: AI tool detection

The plugin SHALL detect all 24 AI tools supported by the OpenSpec CLI using directory scanning, with type classification (CLI vs IDE_PANEL) and CLI ID mapping.

#### Scenario: Tool detection
- **WHEN** `AiToolDetectionService.detect()` scans the project root
- **THEN** it SHALL check all 24 tool directories and classify each as CLI or IDE_PANEL

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

### Requirement: API compatibility

The plugin SHALL avoid scheduled-for-removal APIs and use recommended replacements where available for the supported platform range (2024.2+).

#### Scenario: Plugin Verifier
- **WHEN** Plugin Verifier runs across supported IDE versions
- **THEN** zero override-only violations SHALL be reported

### Requirement: File type registration

The plugin SHALL register `.openspec.yaml` as a custom file type and provide distinct icons for spec files, delta specs, and OpenSpec YAML files.

#### Scenario: File type icons
- **WHEN** a user views `openspec/` files in the project tree
- **THEN** spec files, delta specs, and `.openspec.yaml` files SHALL display their custom icons

### Requirement: Notification system

The plugin SHALL deliver categorized notifications with titles, actions, and bulk operation summaries. API errors SHALL include human-readable messages with actionable suggestions.

#### Scenario: Notifications
- **WHEN** the plugin reports status to the user
- **THEN** notifications SHALL use registered groups, include contextual titles, and provide action links where appropriate

### Requirement: CLI re-detection on tool window activation

The plugin SHALL re-run CLI detection when the OpenSpec tool window is activated (shown). Detection SHALL be throttled to skip if the last detection was within the last 30 seconds. After re-detection, the status bar SHALL update to reflect the current CLI availability.

#### Scenario: Tool window activated with stale detection
- **WHEN** the OpenSpec tool window is shown and the last detection was more than 30 seconds ago
- **THEN** the plugin SHALL re-run CLI detection on a background thread and update the status bar on the EDT

#### Scenario: Tool window activated with fresh detection
- **WHEN** the OpenSpec tool window is shown and the last detection was within 30 seconds
- **THEN** the plugin SHALL skip re-detection

#### Scenario: CLI installed after project open
- **WHEN** the user installs the OpenSpec CLI after the project is opened and then activates the tool window
- **THEN** the status bar SHALL update to show "CLI: available" after re-detection completes

### Requirement: Config profile management

The plugin SHALL allow viewing and switching OpenSpec config profiles (core, expanded, custom) from the Settings panel.

#### Scenario: Profile display
- **WHEN** the user opens OpenSpec Settings
- **THEN** the current profile and active workflows SHALL be displayed

#### Scenario: Profile switch
- **WHEN** the user selects a different profile
- **THEN** the plugin SHALL update the global OpenSpec config and refresh available workflows

### Requirement: CLI update command

The plugin SHALL provide an "Update OpenSpec" action that runs `openspec update` to refresh agent instruction files.

#### Scenario: Update trigger
- **WHEN** the user triggers "Update OpenSpec"
- **THEN** the plugin SHALL run `openspec update` and display the result in the console

#### Scenario: CLI not available
- **WHEN** the CLI is not detected
- **THEN** the action SHALL be disabled with a tooltip explaining that the CLI is required

### Requirement: ComplianceService registration

The plugin SHALL register `ComplianceService` as a project-level service (`@Service(Service.Level.PROJECT)`) in `plugin.xml`. The service SHALL be injectable via `project.getService(ComplianceService.class)` and SHALL compose `BuiltInValidator`, `VerificationService`, and `SpecSyncService` to compute compliance results.

#### Scenario: Service registration
- **WHEN** the plugin starts for a project
- **THEN** `ComplianceService` SHALL be available via `project.getService(ComplianceService.class)`

#### Scenario: Service composes existing services
- **WHEN** `ComplianceService.checkCompliance(change)` is called
- **THEN** it SHALL delegate to `BuiltInValidator` for validation, `VerificationService` for artifact completeness, and `SpecSyncService` for delta spec readiness

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
