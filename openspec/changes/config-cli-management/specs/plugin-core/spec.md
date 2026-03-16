## MODIFIED Requirements

### Requirement: Configuration parsing

The plugin SHALL parse `openspec/config.yaml` and surface clear errors for malformed YAML in both `config.yaml` and `.openspec.yaml`. Additionally, the plugin SHALL support reading the active profile's workflow configuration when the CLI is available.

#### Scenario: Valid config
- **WHEN** a valid `config.yaml` exists
- **THEN** all config values SHALL be accessible via ConfigService

#### Scenario: Malformed YAML
- **WHEN** `config.yaml` or `.openspec.yaml` contains invalid YAML
- **THEN** the plugin SHALL show a warning notification with file path and parse error

#### Scenario: Profile config retrieval via CLI
- **WHEN** the CLI is detected and `openspec config profile --json` succeeds
- **THEN** the plugin SHALL make profile name, description, and active workflows available to the Settings panel

#### Scenario: Profile config retrieval without CLI
- **WHEN** the CLI is not detected
- **THEN** the plugin SHALL fall back to the locally-stored profile name in OpenSpecSettings without workflow details
