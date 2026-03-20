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
- **WHEN** the installed CLI version is below 1.2.0
- **THEN** the plugin SHALL return an empty schema list and disable schema management features

### Requirement: Fork schema

The plugin SHALL allow forking an existing schema into a new custom schema, creating a local schema file that the user can modify.

#### Scenario: Fork built-in schema
- **WHEN** the user selects a schema and provides a new name
- **THEN** the plugin SHALL call `openspec schema fork <source> <name>` and report the path to the new schema file

#### Scenario: Fork clears cache
- **WHEN** a fork operation succeeds
- **THEN** the plugin SHALL clear the cached schema list so the next listing reflects the new schema

### Requirement: Create new schema

The plugin SHALL provide a dialog for creating a new custom schema with a name, optional description, and selectable artifact set.

#### Scenario: New schema dialog
- **WHEN** the user opens the New Schema dialog
- **THEN** the dialog SHALL display a name field, a description field, and checkboxes for artifacts (proposal, design, specs, tasks — all checked by default)

#### Scenario: Name validation
- **WHEN** the user enters a schema name
- **THEN** the dialog SHALL validate that the name follows kebab-case format (`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`) and is non-empty

#### Scenario: Init schema
- **WHEN** the user confirms the dialog with a valid name
- **THEN** the plugin SHALL call `openspec schema init <name>` and report the path to the new schema file

### Requirement: CLI version guard

The plugin SHALL check the CLI version before exposing schema management features, requiring version 1.2.0 or later for schema operations.

#### Scenario: Sufficient CLI version
- **WHEN** the CLI version is 1.2.0 or later
- **THEN** schema listing, fork, and init operations SHALL be available

#### Scenario: Insufficient CLI version
- **WHEN** the CLI version is below 1.2.0 or the CLI is not installed
- **THEN** the plugin SHALL gracefully disable schema management and not display schema-related UI elements
