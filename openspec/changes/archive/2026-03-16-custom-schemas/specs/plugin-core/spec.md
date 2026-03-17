## MODIFIED Requirements

### Requirement: Configuration parsing

The plugin SHALL parse `openspec/config.yaml` and surface clear errors for malformed YAML in both `config.yaml` and `.openspec.yaml`. The plugin SHALL persist a default schema preference in `OpenSpecSettings.State` and expose it via getter/setter methods.

#### Scenario: Valid config
- **WHEN** a valid `config.yaml` exists
- **THEN** all config values SHALL be accessible via ConfigService

#### Scenario: Malformed YAML
- **WHEN** `config.yaml` or `.openspec.yaml` contains invalid YAML
- **THEN** the plugin SHALL show a warning notification with file path and parse error

#### Scenario: Default schema persistence
- **WHEN** the user selects a default schema in Settings
- **THEN** the value SHALL be persisted in `OpenSpecSettings.State.defaultSchema` and available via `OpenSpecSettings.getDefaultSchema()`
