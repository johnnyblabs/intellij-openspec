## MODIFIED Requirements

### Requirement: Propose Action

The plugin SHALL provide a "Propose" action that prompts the user for a change name and optionally a motivation (why) and scope (what changes), then creates a proposal using the OpenSpec 1.2.0 compliant template. After proposing, the Workflow Action Panel SHALL update to show the new change and its first ready artifact.

#### Scenario: Propose with all fields
- **WHEN** the user selects OpenSpec > Propose from the main menu bar
- **THEN** a dialog SHALL appear with a required "Change name" field and optional "Why" and "What Changes" multi-line text areas
- **WHEN** the user fills in all fields and confirms
- **THEN** the plugin SHALL create a proposal.md with the "Why" input in the `## Why` section and the "What Changes" input in the `## What Changes` section

#### Scenario: Propose updates workflow panel
- **WHEN** a change is successfully proposed
- **THEN** the Workflow Action Panel SHALL update to show the new change and its Generate button for the first ready artifact

#### Scenario: Propose with only name
- **WHEN** the user provides only a change name and leaves "Why" and "What Changes" blank
- **THEN** the plugin SHALL create a proposal.md with HTML comment placeholders in the `## Why` and `## What Changes` sections

#### Scenario: Propose dialog field labels
- **WHEN** the Propose New Change dialog is displayed
- **THEN** the text input fields SHALL be labeled "Change name:", "Why:", and "What Changes:"
- **THEN** only "Change name" SHALL be required for validation

## ADDED Requirements

### Requirement: Smart Delivery Method Default

The generate artifact action SHALL select the delivery method automatically based on: saved user preference, configured API provider, or detected AI tools — in that priority order.

#### Scenario: Saved preference used
- **WHEN** the user has a saved preferred delivery method
- **THEN** the generate action SHALL use that method without showing a selection dialog

#### Scenario: No preference but API configured
- **WHEN** no preferred method is saved but a Direct API provider is configured with a valid key
- **THEN** the generate action SHALL default to Direct API

#### Scenario: No preference no API but tools detected
- **WHEN** no preferred method is saved and no API is configured but AI tools are detected (e.g., Claude Code, Copilot)
- **THEN** the generate action SHALL default to clipboard mode with a label reflecting the detected tool (e.g., "Copy for Claude Code")
