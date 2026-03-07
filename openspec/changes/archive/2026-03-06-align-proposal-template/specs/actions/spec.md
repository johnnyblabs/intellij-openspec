## MODIFIED Requirements

### Requirement: Propose Action

The plugin SHALL provide a "Propose" action that prompts the user for a change name and optionally a motivation (why) and scope (what changes), then creates a proposal using the OpenSpec 1.2.0 compliant template.

#### Scenario: Propose with all fields
- **WHEN** the user selects OpenSpec > Propose from the main menu bar
- **THEN** a dialog SHALL appear with a required "Change name" field and optional "Why" and "What Changes" multi-line text areas
- **WHEN** the user fills in all fields and confirms
- **THEN** the plugin SHALL create a proposal.md with the "Why" input in the `## Why` section and the "What Changes" input in the `## What Changes` section

#### Scenario: Propose with only name
- **WHEN** the user provides only a change name and leaves "Why" and "What Changes" blank
- **THEN** the plugin SHALL create a proposal.md with HTML comment placeholders in the `## Why` and `## What Changes` sections

#### Scenario: Propose dialog field labels
- **WHEN** the Propose New Change dialog is displayed
- **THEN** the text input fields SHALL be labeled "Change name:", "Why:", and "What Changes:"
- **THEN** only "Change name" SHALL be required for validation
