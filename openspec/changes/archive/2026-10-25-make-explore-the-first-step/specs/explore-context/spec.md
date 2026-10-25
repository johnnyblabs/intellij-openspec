## ADDED Requirements

### Requirement: Explore entry points

Explore SHALL be presented as the step before Propose: first in the OpenSpec menu and the tool window toolbar, beside every invitation to create a change, and on an active change's right-click menu. Every entry point SHALL be gated on the `explore` workflow like the Explore action.

#### Scenario: Menu order
- **WHEN** the user opens the OpenSpec menu
- **THEN** Explore SHALL appear before Propose, and Propose before Fast-Forward, and Explore SHALL have an icon

#### Scenario: Toolbar order
- **WHEN** the OpenSpec tool window's toolbar is shown
- **THEN** it SHALL show Explore immediately before Propose

#### Scenario: Explore beside a Propose invitation
- **WHEN** the plugin invites the user to create a change: the first-change card, the Browse pane's empty state with no active changes, the workflow panel's "No changes yet" card, or the `Changes` node's right-click menu
- **THEN** an "Explore an idea first" entry SHALL appear beside Propose, and choosing it SHALL start Explore

#### Scenario: Explore this change
- **WHEN** the user chooses "Explore this change..." on an active change
- **THEN** Explore SHALL start with the change's name as the topic: filled into the topic dialog, or into the Explore tab's input when Direct API is the delivery mode

#### Scenario: Only on active changes
- **WHEN** the user right-clicks a spec, a delta spec or an archived change
- **THEN** "Explore this change..." SHALL NOT be offered

#### Scenario: Explore not in the profile
- **WHEN** the active profile doesn't include the `explore` workflow
- **THEN** every Explore entry point SHALL be disabled, as the Explore action is

## MODIFIED Requirements

### Requirement: Topic input dialog

The plugin SHALL display a lightweight dialog prompting the user for an optional explore topic before delivery. The dialog SHALL have a single text field with placeholder text and OK/Cancel buttons. When Explore starts with a topic, such as a change's name, the field SHALL be filled with it and remain editable.

#### Scenario: Dialog with topic
- **WHEN** the user enters a topic and clicks OK
- **THEN** the action SHALL proceed with the entered topic

#### Scenario: Dialog with empty topic
- **WHEN** the user clicks OK without entering text
- **THEN** the action SHALL proceed with a default open exploration topic

#### Scenario: Dialog cancelled
- **WHEN** the user clicks Cancel or presses Escape
- **THEN** the action SHALL abort without any delivery

#### Scenario: Pre-filled topic
- **WHEN** Explore starts with a topic, for example from "Explore this change..."
- **THEN** the dialog's field SHALL contain that topic, and the user SHALL be able to edit it before clicking OK
