## ADDED Requirements

### Requirement: Config profile management

The plugin SHALL allow viewing and switching OpenSpec config profiles (core, expanded, custom) from the Settings panel.

#### Scenario: Profile display
- **WHEN** the user opens OpenSpec Settings
- **THEN** the current profile and active workflows SHALL be displayed

#### Scenario: Profile switch
- **WHEN** the user selects a different profile
- **THEN** the plugin SHALL update the global OpenSpec config and refresh available workflows

### Requirement: CLI update command

The plugin SHALL provide an "Update OpenSpec" action that runs `openspec update` to refresh agent instruction files.

#### Scenario: Update trigger
- **WHEN** the user triggers "Update OpenSpec"
- **THEN** the plugin SHALL run `openspec update` and display the result in the console

#### Scenario: CLI not available
- **WHEN** the CLI is not detected
- **THEN** the action SHALL be disabled with a tooltip explaining that the CLI is required
