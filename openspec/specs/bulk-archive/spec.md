# Bulk Archive

## Purpose
Multi-change archive with conflict detection and sequential spec sync.

## Requirements

### Requirement: Bulk archive dialog

The plugin SHALL provide a dialog listing all active changes with checkboxes, completion status, and conflict indicators, allowing the user to select multiple changes for archiving.

#### Scenario: Change listing
- **WHEN** the user opens the Bulk Archive dialog
- **THEN** the dialog SHALL display each active change with a checkbox, name, artifact completion status (e.g., "4/4 complete"), and a conflict icon if applicable

#### Scenario: Empty state
- **WHEN** no active changes exist
- **THEN** the dialog SHALL display a message indicating no changes are available

#### Scenario: Incomplete change warning
- **WHEN** a selected change has incomplete artifacts or tasks
- **THEN** the dialog SHALL show a warning indicator next to that change

### Requirement: Conflict detection

The plugin SHALL detect when multiple selected changes have delta specs targeting the same capability and display a warning.

#### Scenario: Overlapping capabilities detected
- **WHEN** two or more selected changes have delta specs under `specs/<capability>/` for the same capability name
- **THEN** the dialog SHALL highlight the conflicting changes and list the overlapping capabilities

#### Scenario: No conflicts
- **WHEN** no selected changes share delta spec capabilities
- **THEN** the dialog SHALL show no conflict warnings

#### Scenario: Conflict is non-blocking
- **WHEN** conflicts are detected
- **THEN** the user SHALL still be able to proceed with archiving (conflicts are warnings, not errors)

### Requirement: Sequential archive with spec sync

The plugin SHALL archive selected changes sequentially in the listed order, applying spec sync for each change that has delta specs.

#### Scenario: Successful batch archive
- **WHEN** the user confirms the bulk archive
- **THEN** each selected change SHALL be synced (if it has delta specs) and archived in order, with per-change progress reporting

#### Scenario: Partial failure
- **WHEN** one change fails to archive
- **THEN** the plugin SHALL report the failure and continue attempting to archive remaining changes

#### Scenario: Archive order determines sync priority
- **WHEN** two changes have conflicting delta specs for the same requirement
- **THEN** the last-archived change's spec sync SHALL take precedence (last write wins)
