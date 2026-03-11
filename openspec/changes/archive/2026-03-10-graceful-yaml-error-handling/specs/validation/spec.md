## ADDED Requirements

### Requirement: YAML syntax validation
The plugin SHALL validate YAML syntax in `config.yaml` and `.openspec.yaml` files and report parse errors as IDE inspection problems.

#### Scenario: Malformed YAML in config.yaml
- **WHEN** a user opens or edits a `config.yaml` with invalid YAML syntax
- **THEN** the plugin SHALL display an error-level inspection at the problematic location
- **AND** the error message SHALL include the parse error description from SnakeYAML

#### Scenario: Malformed YAML in .openspec.yaml
- **WHEN** a user opens or edits a `.openspec.yaml` with invalid YAML syntax
- **THEN** the plugin SHALL display an error-level inspection at the problematic location

#### Scenario: Valid YAML passes syntax check
- **WHEN** a user opens a `config.yaml` or `.openspec.yaml` with valid YAML syntax
- **THEN** no YAML syntax inspection problems SHALL be reported
