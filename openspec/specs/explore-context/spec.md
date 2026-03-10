# Explore Context Action

## Purpose
Provide an "Explore..." action in the OpenSpec menu that assembles project context and copies it to the clipboard for use in any AI chat tool.

## Requirements

### Requirement: Explore context action

The plugin SHALL provide an "Explore..." action in the OpenSpec menu that assembles project context and copies it to the clipboard for use in any AI chat tool.

#### Scenario: Explore action triggered
- **WHEN** the user selects OpenSpec > Explore... from the menu
- **THEN** the system SHALL assemble project context including config.yaml contents, active change names, detected AI tools, and recent spec summaries
- **AND** copy the assembled context to the clipboard
- **AND** show a notification: "Context copied — paste into your AI tool to start exploring"

#### Scenario: Explore with active change
- **WHEN** the user triggers Explore and an active change exists
- **THEN** the assembled context SHALL include the active change name, its artifact completion status, and the proposal summary if available

#### Scenario: Explore with no project context
- **WHEN** the user triggers Explore but no OpenSpec config exists
- **THEN** the system SHALL show a notification guiding the user to initialize OpenSpec first
