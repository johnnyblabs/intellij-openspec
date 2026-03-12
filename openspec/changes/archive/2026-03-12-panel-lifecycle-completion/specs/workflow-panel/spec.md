## ADDED Requirements

### Requirement: Archive button in workflow panel
The WorkflowActionPanel SHALL display an Archive button when all tasks for the active change are complete, allowing the user to archive directly from the panel.

#### Scenario: Archive button appears when all tasks complete
- **WHEN** all tasks in tasks.md are marked complete for the active change
- **THEN** the panel SHALL display an "Archive" button in the action row with an archive icon
- **AND** the Generate button SHALL be hidden

#### Scenario: Archive button triggers archive and sync
- **WHEN** the user clicks the Archive button
- **THEN** the panel SHALL call `ChangeService.archiveChange()` to move the change to archive
- **AND** the panel SHALL call `ArchiveSyncService.syncAsync()` to reconcile issue trackers

#### Scenario: Archive button is disabled during archive operation
- **WHEN** an archive operation is in progress
- **THEN** the Archive button SHALL be disabled and display "Archiving..." text

### Requirement: Post-archive confirmation state
The WorkflowActionPanel SHALL display a confirmation state after a successful archive showing what happened and offering a path to the next change.

#### Scenario: Successful archive shows confirmation
- **WHEN** the archive operation completes successfully
- **THEN** the panel SHALL show a success message with the archived change name
- **AND** the pipeline chips SHALL remain visible in all-done state
- **AND** a "Start New Change" button SHALL be displayed

#### Scenario: Sync failure shows recovery option
- **WHEN** the archive succeeds but tracker sync fails
- **THEN** the panel SHALL show archive as complete and sync as failed
- **AND** a "Retry Sync" button SHALL be displayed

#### Scenario: Start New Change triggers Propose
- **WHEN** the user clicks "Start New Change" in the post-archive state
- **THEN** the panel SHALL trigger the OpenSpec Propose action

### Requirement: Change-level validation at phase transitions
The WorkflowActionPanel SHALL auto-validate the active change when it crosses phase boundaries, showing results in the guidance area.

#### Scenario: Validation runs when all artifacts become DONE
- **WHEN** all artifacts for the active change transition to DONE status
- **THEN** the panel SHALL run `BuiltInValidator.validateChange()` on a background thread
- **AND** the results SHALL be displayed in the guidance area

#### Scenario: Validation runs when all tasks complete
- **WHEN** all tasks are marked complete for the active change
- **THEN** the panel SHALL run `BuiltInValidator.validateChange()` on a background thread
- **AND** the results SHALL be displayed in the guidance area

#### Scenario: Validation pass shows success message
- **WHEN** change-level validation completes with no errors or warnings
- **THEN** the guidance area SHALL show a success message such as "All artifacts valid"

#### Scenario: Validation warnings shown but don't block
- **WHEN** change-level validation completes with warnings
- **THEN** the guidance area SHALL show the warning count and first warning description
- **AND** the Apply or Archive button SHALL remain enabled
