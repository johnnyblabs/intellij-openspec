# Config Profile

## Purpose
Display and management of OpenSpec workflow profiles (`core`, `custom`) from the Settings panel, with CLI delegation for profile switching and a post-switch prompt to run `openspec update` (the OpenSpec two-step profile change process).

## Requirements

### Requirement: Config profile display in Settings

The plugin SHALL display the active OpenSpec config profile in a dedicated "Config Profile" section within the Settings panel, showing the profile name, description, and active workflow list.

#### Scenario: Profile section with CLI available
- **WHEN** the user opens OpenSpec Settings and the CLI is detected
- **THEN** the Config Profile section SHALL display the profile name, description, and a list of active workflows retrieved via `openspec config profile --json`

#### Scenario: Profile section without CLI
- **WHEN** the user opens OpenSpec Settings and the CLI is not detected
- **THEN** the Config Profile section SHALL display the locally-stored profile name with a label indicating "CLI required for profile details"

#### Scenario: Profile section updates on selection change
- **WHEN** the user selects a different profile in the workflow profile combo
- **THEN** the Config Profile section SHALL refresh to show the selected profile's details

### Requirement: Profile switch via CLI delegation

The plugin SHALL delegate profile changes to the OpenSpec CLI via `openspec config profile <name>` to ensure the CLI's config.yaml remains the source of truth.

#### Scenario: Successful profile switch
- **WHEN** the user selects a different profile and clicks Apply
- **THEN** the plugin SHALL run `openspec config profile <name>` via CliRunner and persist the new profile to OpenSpecSettings upon success

#### Scenario: Profile switch CLI failure
- **WHEN** the CLI command `openspec config profile <name>` fails (non-zero exit code or CLI unavailable)
- **THEN** the plugin SHALL show a warning notification with the error message and retain the previous profile value in OpenSpecSettings

#### Scenario: Profile switch without CLI
- **WHEN** the CLI is not detected and the user changes the profile
- **THEN** the plugin SHALL persist the profile value locally to OpenSpecSettings without CLI delegation and show an informational notification that the change is local-only

### Requirement: Workflow toggles display

The plugin SHALL display active workflows for the current profile as a read-only list in the Config Profile section.

#### Scenario: Workflow list rendering
- **WHEN** the Config Profile section loads with CLI-provided profile data
- **THEN** it SHALL display each active workflow name as a read-only labeled item

#### Scenario: Empty workflow list
- **WHEN** the profile has no active workflows or the data is unavailable
- **THEN** the workflow list SHALL display "No workflow information available"