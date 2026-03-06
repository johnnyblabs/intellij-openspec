# Actions

## Domain
CLI workflow automation through IDE menu actions and shortcuts.

## Requirements

### Requirement: CLI Integration

The plugin SHALL execute OpenSpec CLI commands as external processes and capture their output.

**Keyword:** SHALL

#### Scenarios

**Scenario: Run CLI command**
- GIVEN the OpenSpec CLI is installed on the system
- WHEN the user triggers an action (e.g., validate)
- THEN the plugin SHALL execute the corresponding `openspec` CLI command
- AND display the output in the IDE console

### Requirement: Propose Action

The plugin SHALL provide a "Propose" action that prompts the user for a change description and runs `openspec propose`.

**Keyword:** SHALL

#### Scenarios

**Scenario: Propose with description**
- GIVEN an OpenSpec project is open
- WHEN the user selects OpenSpec > Propose from the main menu bar
- THEN a dialog SHALL appear requesting a change description
- AND the plugin SHALL run `openspec propose` with the provided description

### Requirement: Action Availability

Actions SHALL only be enabled when the current project is an OpenSpec project.

**Keyword:** SHALL

#### Scenarios

**Scenario: Non-OpenSpec project**
- GIVEN a project without an `openspec/` directory
- WHEN the user opens the OpenSpec menu
- THEN OpenSpec actions SHALL be disabled
