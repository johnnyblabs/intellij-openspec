## ADDED Requirements

### Requirement: Change Selector Display

The WorkflowActionPanel SHALL display a change selector that shows the currently active change and allows switching between active changes.

#### Scenario: Single active change
- **WHEN** only one active change exists
- **THEN** the panel SHALL display the change name as a plain label without dropdown controls

#### Scenario: Multiple active changes
- **WHEN** multiple active changes exist
- **THEN** the panel SHALL display a dropdown allowing the user to select which change to work on

#### Scenario: No active changes
- **WHEN** no active changes exist
- **THEN** the panel SHALL display guidance text such as "No active change" with a hint to use Propose

### Requirement: Change Selection Persistence

The change selector SHALL remember the user's selection within a session and update when changes are created or archived.

#### Scenario: Selection persists during session
- **WHEN** the user selects a change from the dropdown
- **THEN** the panel SHALL continue showing that change until the user selects a different one or the change is archived

#### Scenario: New change auto-selects
- **WHEN** a new change is proposed and no change was previously selected
- **THEN** the panel SHALL automatically select the new change

#### Scenario: Archived change deselects
- **WHEN** the currently selected change is archived
- **THEN** the panel SHALL auto-select the next available active change, or show the empty state if none remain
