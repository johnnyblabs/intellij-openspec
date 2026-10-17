## MODIFIED Requirements

### Requirement: Independent version-axis resolution

The plugin SHALL resolve and expose two independent version axes without conflating them: the **CLI version** (floor 1.3.0, baseline 1.4.x) and the **config-format version** (`openspec/config.yaml` `version:`). Version-dependent behavior SHALL select the axis that actually governs it. The plugin SHALL NOT offer a config-format version override while that axis models a single format, and SHALL ignore an override stored by an earlier version, so no setting can apply invisibly or offer a value that is silently ignored.

#### Scenario: CLI version gates capability availability
- **WHEN** behavior depends on whether a command or schema exists in the installed client
- **THEN** the plugin SHALL evaluate the CLI version axis (e.g. 1.3 vs 1.4), not the config-format version

#### Scenario: Config-format version is preserved for effective-version resolution
- **WHEN** the plugin resolves the effective version for self-validation
- **THEN** it SHALL continue to read the config-format `version:` field and SHALL NOT substitute the CLI version for it

#### Scenario: Version-override UI reflects the config-format axis
- **WHEN** the config-format axis models a single format (currently `1.2.0`)
- **THEN** the settings SHALL show no version-override control, and an override value stored by an earlier plugin version SHALL NOT affect the effective version, which resolves from `openspec/config.yaml`'s `version:` or the `1.2.0` default
