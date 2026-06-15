## MODIFIED Requirements

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

### Requirement: CLI version guard

The plugin SHALL check the CLI version before exposing schema management features, requiring version 1.3.0 or later for schema operations. CLI 1.4.x is the recommended version; 1.3.x remains supported for the duration of this release. The floor SHALL be sourced from `SchemaService.MIN_CLI_VERSION` — this requirement SHALL track that constant rather than encoding a literal version, so a future floor bump only touches the constant and one spec line.

#### Scenario: Sufficient CLI version
- **WHEN** the CLI version is 1.3.0 or later
- **THEN** schema listing, fork, and init operations SHALL be available

#### Scenario: Recommended CLI version
- **WHEN** the CLI version is 1.4.0 or later
- **THEN** schema management SHALL function with full feature parity (interactive `openspec config profile` picker delegation, 30-tool detection, `workspace-planning` schema recognition via CLI runtime)

#### Scenario: Supported but below recommended
- **WHEN** the CLI version is in the range `[1.3.0, 1.4.0)`
- **THEN** schema management SHALL still function — the user MAY see a recommendation to upgrade to 1.4.x via existing surfaces (notification, status badge), but no schema management feature SHALL be gated specifically on 1.4.x

#### Scenario: Insufficient CLI version
- **WHEN** the CLI version is below 1.3.0 or the CLI is not installed
- **THEN** the plugin SHALL gracefully disable schema management and not display schema-related UI elements