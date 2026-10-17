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

### Requirement: Bulk archive runs sequentially with spec sync and always completes

The plugin SHALL archive the selected changes one at a time in the listed order, with the work off the UI thread. With OpenSpec CLI 1.5 or later, each change SHALL be synced and archived in one `openspec archive <name> --yes --json` call; otherwise the plugin SHALL apply spec sync first for each change that has delta specs and then archive it itself. Each row's status SHALL update in the open dialog as it progresses. When the run ends, successfully or not, the plugin SHALL post a summary and re-enable the dialog's OK button so the dialog can be closed.

#### Scenario: Successful batch archive
- **WHEN** the user confirms the bulk archive
- **THEN** each selected change SHALL be synced (if it has delta specs) and archived in order, and its row SHALL show "Archiving..." then "Done" while the dialog is open

#### Scenario: Partial failure
- **WHEN** one change fails to sync or archive, including when the CLI refuses it
- **THEN** its row SHALL show "Failed" with the reason (the CLI's message when the CLI refused), and the remaining changes SHALL still be processed

#### Scenario: Archive order determines sync priority
- **WHEN** two changes have conflicting delta specs for the same requirement
- **THEN** the last-archived change's spec sync SHALL take precedence (last write wins)

#### Scenario: The dialog can always be closed afterwards
- **WHEN** the archive run ends, including when it stops on an unexpected error
- **THEN** the summary notification SHALL be posted and the OK button SHALL be re-enabled

### Requirement: Change status fills while the dialog is open

Each change's artifact completion status SHALL be shown in the open Bulk Archive dialog as soon as it is computed, not held until the dialog closes. Results that arrive after the dialog has closed SHALL be discarded.

#### Scenario: Status column fills
- **WHEN** the user opens the Bulk Archive dialog
- **THEN** each row's status SHALL change from "loading..." to its artifact completion (e.g. "4/4 complete") while the dialog is open

### Requirement: Changes with a blocking sync result are not archived

Before writing any spec, Bulk Archive SHALL run the spec-sync check for every selected change. A change whose sync reports a blocking problem, such as a MODIFIED or REMOVED target missing from the main spec or a RENAMED entry that is unpaired or names a missing requirement, SHALL NOT be synced or archived; its row SHALL show it as blocked with the reason. The check SHALL run again just before each change is written.

#### Scenario: A change with a missing MODIFIED target is blocked
- **WHEN** a selected change has a MODIFIED delta for a requirement its main spec doesn't contain
- **THEN** that change SHALL NOT be synced or archived, its row SHALL show "Blocked" with the reason, and the other selected changes SHALL still be archived

#### Scenario: Nothing is written before every change is checked
- **WHEN** the user confirms a bulk archive
- **THEN** every selected change SHALL be checked before any spec is written or any change is archived
