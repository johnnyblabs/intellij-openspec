## ADDED Requirements

### Requirement: Multi-change archive

The plugin SHALL provide a Bulk Archive action that archives multiple completed changes in a single operation with conflict detection.

#### Scenario: Change selection
- **WHEN** the user triggers Bulk Archive
- **THEN** the plugin SHALL display a dialog listing all active changes with checkboxes, showing per-change status (task completion, artifact completeness)

#### Scenario: Conflict detection
- **WHEN** two or more selected changes have delta specs touching the same spec domain
- **THEN** the plugin SHALL highlight the conflict and warn the user before proceeding

#### Scenario: Sequential archive
- **WHEN** the user confirms the selection
- **THEN** the plugin SHALL archive changes sequentially, running spec sync for each, and display a summary notification

### Requirement: Bulk archive status display

The plugin SHALL show per-change readiness information in the bulk archive dialog.

#### Scenario: Task completion display
- **WHEN** the dialog is shown
- **THEN** each change SHALL display task completion percentage and artifact completeness status

#### Scenario: Incomplete change warning
- **WHEN** a selected change has incomplete tasks or missing artifacts
- **THEN** the dialog SHALL show a warning icon with details about what is incomplete
