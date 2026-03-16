## ADDED Requirements

### Requirement: Schema listing and selection

The plugin SHALL display available workflow schemas in the Settings panel and allow the user to select a default schema for new changes.

#### Scenario: List schemas
- **WHEN** the user opens OpenSpec Settings
- **THEN** the Schemas section SHALL list all available schemas from `openspec schemas --json` with name and description

#### Scenario: Set default
- **WHEN** the user selects a schema as default
- **THEN** the plugin SHALL update the project's `config.yaml` to use that schema for new changes

### Requirement: Schema forking

The plugin SHALL allow forking an existing schema for project-level customization via `openspec schema fork`.

#### Scenario: Fork schema
- **WHEN** the user clicks "Fork" on a schema
- **THEN** the plugin SHALL run `openspec schema fork <source> <name>` and open the forked schema file in the editor

### Requirement: Schema creation

The plugin SHALL allow creating a new project-local schema with a custom artifact sequence via `openspec schema init`.

#### Scenario: Create schema
- **WHEN** the user clicks "New Schema"
- **THEN** the plugin SHALL present a dialog for name, description, and artifact selection, then run `openspec schema init <name>` with the configured options

### Requirement: Schema-aware change creation

All change creation workflows (Propose, FF, New) SHALL offer schema selection when multiple schemas are available.

#### Scenario: Schema picker in dialogs
- **WHEN** multiple schemas exist and the user creates a change
- **THEN** the dialog SHALL include a schema dropdown defaulting to the project's configured default
