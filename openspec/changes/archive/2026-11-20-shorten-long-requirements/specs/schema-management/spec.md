## MODIFIED Requirements

### Requirement: CLI version guard

The plugin SHALL check the CLI version before exposing schema management, requiring 1.3.0 or later for schema operations. The floor SHALL be read from `SchemaService.MIN_CLI_VERSION` and not encoded as a literal. The validate, provenance and open-templates operations SHALL sit behind the same guard, and any whose command is absent on a 1.3.x CLI SHALL be disabled below 1.4.0 with the upgrade recommendation.

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

#### Scenario: Recommended and supported versions
- **WHEN** the detected CLI is 1.4.x
- **THEN** it is the recommended version
- **AND** when the detected CLI is 1.3.x, schema management remains supported for the duration of this release
