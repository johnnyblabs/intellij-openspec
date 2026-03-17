# Custom Schemas

## Purpose
Schema management — list, select, fork, and create workflow schemas from the IDE, with CLI delegation and Settings panel integration.

## Requirements

### Requirement: Schema listing

The plugin SHALL retrieve available schemas by calling `openspec schemas --json` and parsing the JSON response into a list of schema records containing name, description, built-in flag, and artifact identifiers.

#### Scenario: List available schemas
- **WHEN** `SchemaService.listSchemas()` is called
- **THEN** the service SHALL execute `openspec schemas --json` via `CliRunner` and return a list of `SchemaInfo` records

#### Scenario: CLI unavailable
- **WHEN** the CLI is not detected or the schema command is unsupported
- **THEN** the service SHALL return an empty list and report the limitation via notification

### Requirement: Schema forking

The plugin SHALL fork an existing schema by calling `openspec schema fork <source> <name>` and opening the resulting schema file in the editor.

#### Scenario: Fork a built-in schema
- **WHEN** the user selects a schema and clicks "Fork" in the Settings panel
- **THEN** the plugin SHALL call `openspec schema fork <source> <name>` via `CliRunner`, refresh VFS, and open the forked schema file in `FileEditorManager`

#### Scenario: Fork failure
- **WHEN** the fork CLI command fails (non-zero exit code)
- **THEN** the plugin SHALL display an error notification with the CLI stderr output

### Requirement: Schema creation

The plugin SHALL create a new schema via `NewSchemaDialog` that captures name, description, and artifact selection, then delegates to `openspec schema init <name>`.

#### Scenario: Create new schema
- **WHEN** the user fills in the New Schema dialog and clicks OK
- **THEN** the plugin SHALL call `openspec schema init <name>` via `CliRunner`, refresh VFS, and open the new schema file in the editor

#### Scenario: Name validation
- **WHEN** the user enters a schema name that is blank or not kebab-case
- **THEN** the dialog SHALL display a validation error and prevent submission

### Requirement: Schema settings section

The plugin SHALL display a "Schemas" section in the OpenSpec Settings panel containing a list of available schemas, a default schema selector, and Fork/New/Refresh action buttons.

#### Scenario: Settings panel display
- **WHEN** the user opens OpenSpec Settings
- **THEN** the Schemas section SHALL show a `JBList` of available schemas with name and description, a default schema combo box, and Fork/New/Refresh buttons

#### Scenario: Default schema selection
- **WHEN** the user selects a default schema from the combo box and applies settings
- **THEN** the selected schema name SHALL be persisted in `OpenSpecSettings.State.defaultSchema`

#### Scenario: Refresh action
- **WHEN** the user clicks the Refresh button
- **THEN** the schema list SHALL be reloaded from the CLI, clearing any cached results

### Requirement: Schema selector in change dialogs

The plugin SHALL add a schema dropdown to `ProposeChangeDialog` and `FfDialog` that is visible only when multiple schemas are available and passes the selected schema to the `openspec new change` command.

#### Scenario: Multiple schemas available
- **WHEN** `SchemaService.listSchemas()` returns more than one schema
- **THEN** both `ProposeChangeDialog` and `FfDialog` SHALL display a schema combo box pre-selected to the default schema

#### Scenario: Single schema available
- **WHEN** only one schema is available
- **THEN** the schema combo box SHALL NOT be visible in either dialog

#### Scenario: Schema passed to CLI
- **WHEN** the user creates a change with a schema selected
- **THEN** the plugin SHALL pass `--schema "<schema>"` to the `openspec new change` CLI command

### Requirement: CLI version guard

The plugin SHALL check that the detected CLI version supports schema commands before enabling schema features, and display a clear message when the CLI version is insufficient.

#### Scenario: Supported CLI version
- **WHEN** the detected CLI version supports schema commands
- **THEN** all schema features (list, fork, new, selector) SHALL be enabled

#### Scenario: Unsupported CLI version
- **WHEN** the CLI is older than the minimum required version for schema support
- **THEN** the Settings Schemas section SHALL display a message indicating the minimum CLI version required, and Fork/New buttons SHALL be disabled

### Requirement: Schema list caching

The plugin SHALL cache the schema list within a settings panel session and invalidate the cache when a fork or init operation completes.

#### Scenario: Cached list reuse
- **WHEN** `listSchemas()` is called multiple times within the same settings session
- **THEN** the CLI SHALL be invoked only once, with subsequent calls returning the cached result

#### Scenario: Cache invalidation on mutation
- **WHEN** a fork or init operation completes successfully
- **THEN** the cached schema list SHALL be cleared so the next call fetches fresh data from the CLI
