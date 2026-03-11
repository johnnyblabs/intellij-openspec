## MODIFIED Requirements

### Requirement: Config Parsing
The plugin SHALL parse the `openspec/config.yaml` file and make its contents available to all plugin services. When parsing fails, the plugin SHALL show a clear error notification.

#### Scenario: Valid config file
- GIVEN a valid `openspec/config.yaml` with schema, profile, and rules
- WHEN the config service initializes
- THEN all config values SHALL be accessible via the ConfigService API

#### Scenario: Missing config file
- GIVEN an `openspec/` directory without a `config.yaml` file
- WHEN the config service initializes
- THEN the service SHALL report an error
- AND the plugin SHOULD notify the user

#### Scenario: Malformed config YAML
- **WHEN** the config service loads a `config.yaml` that contains invalid YAML syntax
- **THEN** the service SHALL show a warning notification with the file path, line number, and parse error description
- **AND** the error SHALL be logged at WARN level
- **AND** the config SHALL be treated as missing (null)

## ADDED Requirements

### Requirement: Change metadata parse error reporting
The plugin SHALL show a warning notification when `.openspec.yaml` contains invalid YAML, rather than silently skipping the metadata.

#### Scenario: Malformed change metadata
- **WHEN** ChangeService loads a `.openspec.yaml` that contains invalid YAML syntax
- **THEN** the service SHALL show a warning notification with the file path and parse error description
- **AND** the change SHALL still appear in the tree (without metadata)
