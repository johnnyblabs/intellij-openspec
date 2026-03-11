## ADDED Requirements

### Requirement: Workflow panel shows archive and sync outcomes
The WorkflowActionPanel SHALL surface distinct archive and sync outcomes so users can understand completion state and next actions.

#### Scenario: Archive and sync both succeed
- **WHEN** archive and sync complete successfully for the selected change
- **THEN** the panel SHALL show a success outcome indicating archive and sync are complete

#### Scenario: Archive succeeds but sync fails
- **WHEN** archive completes and sync fails for the selected change
- **THEN** the panel SHALL show archive as complete and sync as failed with recovery guidance

### Requirement: Workflow panel provides sync recovery action
The WorkflowActionPanel SHALL provide a retry path when sync fails after archive.

#### Scenario: Sync retry is available after sync failure
- **WHEN** the panel displays a sync failure for an archived change
- **THEN** the panel SHALL offer a retry action for sync reconciliation

#### Scenario: Sync retry refreshes panel state
- **WHEN** the user triggers sync retry from the panel and retry succeeds
- **THEN** the panel SHALL refresh and show synchronized completion state

