# Schema Management

## Purpose
Browsing, forking, and creating custom OpenSpec workflow schemas via the CLI, with graceful degradation when the CLI is unavailable or below the minimum version.

## Requirements

### Requirement: Schema listing

The plugin SHALL list available schemas from the OpenSpec CLI, showing each schema's name, description, built-in status, and artifact definitions. Results SHALL be cached until a fork or init operation clears the cache.

#### Scenario: List schemas
- **WHEN** the user accesses schema management
- **THEN** the plugin SHALL call `openspec schemas --json` and display the available schemas

#### Scenario: Cache hit
- **WHEN** schemas have been listed previously and no fork or init has occurred
- **THEN** the plugin SHALL return the cached schema list without calling the CLI

#### Scenario: CLI below minimum version
- **WHEN** the installed CLI version is below 1.3.0
- **THEN** the plugin SHALL return an empty schema list and disable schema management features

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

### Requirement: Schema validation action

The plugin SHALL let the user validate a schema from the schema management UI by delegating to `openspec schema validate <name> --json`, presenting the reported problems (structure errors, missing templates, circular dependencies) with their severity inline in the schema section. The CLI call SHALL run off the EDT. Parsing of the CLI output SHALL be covered by a contract test against captured real CLI output.

#### Scenario: Validate a well-formed schema
- **WHEN** the user invokes Validate on a schema and the CLI reports no problems
- **THEN** the plugin SHALL confirm the schema is valid in the schema section

#### Scenario: Validate a broken schema
- **WHEN** the user invokes Validate on a schema and the CLI reports problems
- **THEN** the plugin SHALL display each reported problem with its severity, without opening a modal error dialog

#### Scenario: Validation CLI failure
- **WHEN** the `schema validate` invocation itself fails (non-zero exit without parseable JSON)
- **THEN** the plugin SHALL surface the CLI's error output through the existing schema-management error surface

### Requirement: Schema resolution provenance

The plugin SHALL show where each listed schema resolves from — project (`openspec/schemas/`), user, or package built-in — sourced from `openspec schema which <name> --json`, refreshed on the same cache lifecycle as the schema listing (invalidated by fork/init/refresh, not per-selection).

#### Scenario: Project schema shadows a built-in
- **WHEN** a project-local schema has the same name as a package built-in
- **THEN** the schema list SHALL mark the schema as resolving from the project

#### Scenario: Provenance unavailable
- **WHEN** `schema which` fails or returns no resolution for a schema
- **THEN** the plugin SHALL omit the origin tag for that schema rather than blocking the listing

### Requirement: Open schema templates

The plugin SHALL let the user open a schema's artifact templates in the editor, resolving paths via `openspec templates --schema <name> --json`. Existing template files SHALL open as ordinary editor tabs; paths that do not exist on disk SHALL be skipped and reported, and the plugin SHALL NOT create template files itself.

#### Scenario: Open templates for a forked schema
- **WHEN** the user invokes Open Templates on a schema whose template files exist
- **THEN** the plugin SHALL open each resolved template file in the editor

#### Scenario: Missing template file
- **WHEN** a resolved template path does not exist on disk
- **THEN** the plugin SHALL skip it and report the missing path instead of creating the file

### Requirement: CLI version guard

The plugin SHALL check the CLI version before exposing schema management features, requiring version 1.3.0 or later for schema operations. CLI 1.4.x is the recommended version; 1.3.x remains supported for the duration of this release. The floor SHALL be sourced from `SchemaService.MIN_CLI_VERSION` — this requirement SHALL track that constant rather than encoding a literal version, so a future floor bump only touches the constant and one spec line. The validate, provenance, and open-templates operations SHALL sit behind the same guard; if implementation-time verification finds any of their underlying commands absent on a 1.3.x CLI, that operation SHALL be disabled below 1.4.0 with the existing upgrade recommendation instead of failing at invocation time.

#### Scenario: Sufficient CLI version
- **WHEN** the CLI version is 1.3.0 or later
- **THEN** schema listing, fork, and init operations SHALL be available

#### Scenario: Recommended CLI version
- **WHEN** the CLI version is 1.4.0 or later
- **THEN** schema management SHALL function with full feature parity (interactive `openspec config profile` picker delegation, 30-tool detection, `workspace-planning` schema recognition via CLI runtime), including schema validation, resolution provenance, and template opening

#### Scenario: Supported but below recommended
- **WHEN** the CLI version is in the range `[1.3.0, 1.4.0)`
- **THEN** schema management SHALL still function — the user MAY see a recommendation to upgrade to 1.4.x via existing surfaces (notification, status badge); only operations whose underlying CLI commands are verified absent on 1.3.x MAY be disabled with the upgrade hint

#### Scenario: Insufficient CLI version
- **WHEN** the CLI version is below 1.3.0 or the CLI is not installed
- **THEN** the plugin SHALL gracefully disable schema management and not display schema-related UI elements

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
