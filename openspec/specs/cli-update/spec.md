# CLI Update

## Purpose
Update action that runs `openspec update` to refresh agent instruction files, with CLI availability guards.

## Requirements

### Requirement: Update action triggers CLI update

The plugin SHALL provide an "Update OpenSpec" action that runs `openspec update` in the background and displays the output in the OpenSpec console panel.

#### Scenario: Successful update
- **WHEN** the user triggers the Update OpenSpec action and the CLI is available
- **THEN** the plugin SHALL run `openspec update` via CliRunner, display stdout in the console panel, and show a success notification upon completion

#### Scenario: Update with errors
- **WHEN** `openspec update` exits with a non-zero exit code
- **THEN** the plugin SHALL display stderr in the console panel and show an error notification with the exit code

#### Scenario: Update progress
- **WHEN** the update command is running
- **THEN** the plugin SHALL show a background progress indicator with the label "Running openspec update"

### Requirement: Update action CLI guard

The Update OpenSpec action SHALL be disabled when the OpenSpec CLI is not detected, with an explanatory tooltip.

#### Scenario: CLI not detected
- **WHEN** the action update cycle runs and CliDetectionService reports the CLI is unavailable
- **THEN** the action SHALL be disabled and its description SHALL indicate that the CLI is required

#### Scenario: CLI detected
- **WHEN** the action update cycle runs and CliDetectionService reports the CLI is available
- **THEN** the action SHALL be enabled with its standard description

#### Scenario: Non-OpenSpec project
- **WHEN** the current project is not an OpenSpec project
- **THEN** the action SHALL be hidden (not visible in menus)

### Requirement: Update action registration

The Update OpenSpec action SHALL be registered in plugin.xml within the OpenSpec main menu group and accessible from the tool window toolbar.

#### Scenario: Menu placement
- **WHEN** the user opens the OpenSpec menu
- **THEN** "Update OpenSpec" SHALL appear in the utility section after the Refresh action

#### Scenario: Toolbar placement
- **WHEN** the user views the OpenSpec tool window toolbar
- **THEN** an Update button SHALL be available in the toolbar group