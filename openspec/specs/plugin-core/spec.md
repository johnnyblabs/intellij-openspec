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

The plugin SHALL parse `openspec/config.yaml` and surface clear errors for malformed YAML in both `config.yaml` and `.openspec.yaml`.

#### Scenario: Valid config
- **WHEN** a valid `config.yaml` exists
- **THEN** all config values SHALL be accessible via ConfigService

#### Scenario: Malformed YAML
- **WHEN** `config.yaml` or `.openspec.yaml` contains invalid YAML
- **THEN** the plugin SHALL show a warning notification with file path and parse error

### Requirement: AI tool detection

The plugin SHALL detect all 24 AI tools supported by the OpenSpec CLI using directory scanning, with type classification (CLI vs IDE_PANEL) and CLI ID mapping.

#### Scenario: Tool detection
- **WHEN** `AiToolDetectionService.detect()` scans the project root
- **THEN** it SHALL check all 24 tool directories and classify each as CLI or IDE_PANEL

### Requirement: VFS-consistent file operations

All file writes SHALL use IntelliJ's VirtualFile API within `WriteAction`. CLI-created files SHALL be followed by synchronous VFS refresh. Tool window refresh SHALL use `syncRefresh()`.

#### Scenario: Files visible after write
- **WHEN** the plugin writes files (init, propose, generate)
- **THEN** they SHALL be immediately visible in the project tree without async delay

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
