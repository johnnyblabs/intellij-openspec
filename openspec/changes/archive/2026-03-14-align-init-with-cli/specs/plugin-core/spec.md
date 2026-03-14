## MODIFIED Requirements

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

## ADDED Requirements

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
