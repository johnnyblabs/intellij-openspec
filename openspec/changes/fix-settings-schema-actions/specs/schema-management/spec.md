## MODIFIED Requirements

### Requirement: Fork schema

The plugin SHALL allow forking an existing schema into a new custom schema, creating a local schema file that the user can modify. After a successful fork, the plugin SHALL open the new schema's `schema.yaml` in the editor, taking its location from the CLI's structured output rather than from human-readable text.

#### Scenario: Fork built-in schema
- **WHEN** the user selects a schema and provides a new name
- **THEN** the plugin SHALL call `openspec schema fork <source> <name> --json`, read the new schema's directory from the reported `destinationPath`, and open its `schema.yaml` in the editor

#### Scenario: Fork clears cache
- **WHEN** a fork operation succeeds
- **THEN** the plugin SHALL clear the cached schema list so the next listing reflects the new schema

### Requirement: Create new schema

The plugin SHALL provide a dialog for creating a new custom schema with a name, optional description, and selectable artifact set, and SHALL create the schema with exactly the description and artifacts the user chose.

#### Scenario: New schema dialog
- **WHEN** the user opens the New Schema dialog
- **THEN** the dialog SHALL display a name field, a description field, and checkboxes for artifacts (proposal, design, specs, tasks — all checked by default)

#### Scenario: Name validation
- **WHEN** the user enters a schema name
- **THEN** the dialog SHALL validate that the name follows kebab-case format (`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`) and is non-empty

#### Scenario: Init schema
- **WHEN** the user confirms the dialog with a valid name
- **THEN** the plugin SHALL call `openspec schema init <name> --json --no-default`, passing `--description` when a description was entered and `--artifacts` with the selected artifacts, read the new schema's directory from the reported `path`, and open its `schema.yaml` in the editor

## ADDED Requirements

### Requirement: Settings results appear while the dialog is open

Every result the OpenSpec settings page computes in the background SHALL be shown in the open Settings dialog as soon as it is ready, not held until the dialog closes. This covers the schema list and default-schema choice, schema validation results, opened templates and schemas, and the config and workflow-profile sections.

#### Scenario: Schema list fills while Settings is open
- **WHEN** the user opens the OpenSpec settings page with a supported CLI
- **THEN** the schema list and the default-schema choice SHALL show the CLI's schemas without the user having to close and reopen the dialog

#### Scenario: Refresh updates the open dialog
- **WHEN** the user clicks the schema section's Refresh
- **THEN** the reloaded list SHALL appear in the open dialog, and any previous schema validation result SHALL be cleared, because the reload drops the selection it described

#### Scenario: Validate shows its result in place
- **WHEN** the user validates a schema from the open Settings dialog
- **THEN** the validation result SHALL appear in the schema section while the dialog is still open

### Requirement: Schema actions that need a selection are disabled without one

Actions that operate on the selected schema (Fork, Validate and Open Templates) SHALL be disabled while no schema is selected, with a hint to select one, so that a click never silently does nothing. Actions that don't need a selection (New and Refresh) SHALL stay available.

#### Scenario: Nothing selected
- **WHEN** the schema list has no selection
- **THEN** Fork, Validate and Open Templates SHALL be disabled with a hint to select a schema, and New and Refresh SHALL remain enabled

#### Scenario: A schema is selected
- **WHEN** the user selects a schema in the list
- **THEN** Fork, Validate and Open Templates SHALL become enabled
