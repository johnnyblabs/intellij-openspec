# Actions

## Purpose
CLI workflow automation through IDE menu actions and shortcuts.

## Requirements

### Requirement: CLI Integration

The plugin SHALL execute OpenSpec CLI commands as external processes and capture their output.

#### Scenario: Run CLI command
- GIVEN the OpenSpec CLI is installed on the system
- WHEN the user triggers an action (e.g., validate)
- THEN the plugin SHALL execute the corresponding `openspec` CLI command
- AND display the output in the IDE console

### Requirement: Propose Action

The plugin SHALL provide a "Propose" action that prompts the user for a change name and optionally a motivation (why) and scope (what changes), then creates a proposal using the OpenSpec 1.2.0 compliant template. After proposing, the Workflow Action Panel SHALL update to show the new change and its first ready artifact.

#### Scenario: Propose with all fields
- WHEN the user selects OpenSpec > Propose from the main menu bar
- THEN a dialog SHALL appear with a required "Change name" field and optional "Why" and "What Changes" multi-line text areas
- WHEN the user fills in all fields and confirms
- THEN the plugin SHALL create a proposal.md with the "Why" input in the `## Why` section and the "What Changes" input in the `## What Changes` section

#### Scenario: Propose updates workflow panel
- WHEN a change is successfully proposed
- THEN the Workflow Action Panel SHALL update to show the new change and its Generate button for the first ready artifact

#### Scenario: Propose with only name
- WHEN the user provides only a change name and leaves "Why" and "What Changes" blank
- THEN the plugin SHALL create a proposal.md with HTML comment placeholders in the `## Why` and `## What Changes` sections

#### Scenario: Propose dialog field labels
- WHEN the Propose New Change dialog is displayed
- THEN the text input fields SHALL be labeled "Change name:", "Why:", and "What Changes:"
- THEN only "Change name" SHALL be required for validation

### Requirement: Action Availability

Actions SHALL only be enabled when the current project is an OpenSpec project. The menu bar SHALL NOT include Generate Artifact or Generate All Artifacts actions.

#### Scenario: Non-OpenSpec project
- GIVEN a project without an `openspec/` directory
- WHEN the user opens the OpenSpec menu
- THEN OpenSpec actions SHALL be disabled

#### Scenario: OpenSpec menu contents
- WHEN the user opens the OpenSpec menu
- THEN the menu SHALL contain: Init, Propose, Apply, Archive, Validate, List, Refresh
- THEN the menu SHALL NOT contain Generate Artifact or Generate All Artifacts
