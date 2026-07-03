## MODIFIED Requirements

### Requirement: Independent version-axis resolution

The plugin SHALL resolve and expose two independent version axes without conflating them: the **CLI version** (floor 1.3.0, baseline 1.4.x) and the **config-format version** (`openspec/config.yaml` `version:`). Version-dependent behavior SHALL select the axis that actually governs it. Any UI that overrides a version SHALL present only values the targeted axis actually models, so that a selectable value cannot be silently ignored.

#### Scenario: CLI version gates capability availability
- **WHEN** behavior depends on whether a command or schema exists in the installed client
- **THEN** the plugin SHALL evaluate the CLI version axis (e.g. 1.3 vs 1.4), not the config-format version

#### Scenario: Config-format version is preserved for effective-version resolution
- **WHEN** the plugin resolves the effective version for self-validation
- **THEN** it SHALL continue to read the config-format `version:` field and SHALL NOT substitute the CLI version for it

#### Scenario: Version-override UI reflects the config-format axis
- **WHEN** the settings expose a config-format version override
- **THEN** the override SHALL present only values the config-format axis actually models (currently `1.2.0`), and SHALL NOT offer CLI-version-looking values (e.g. `1.3.0`/`1.4.0`) that the config-format axis does not distinguish and that would be silently ignored
