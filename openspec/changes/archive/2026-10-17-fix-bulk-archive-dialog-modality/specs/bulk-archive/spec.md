## REMOVED Requirements

### Requirement: Sequential archive with spec sync

**Reason**: It prescribed an implementation (`invokeLater` dispatch, a ban on `invokeAndWait`, an `AtomicInteger` counter the code never had) that only worked by inheriting the dialog's modality, deadlocked in tests, and could leave the dialog stuck.
**Migration**: Replaced by "Bulk archive runs sequentially with spec sync and always completes", which keeps the sequential order, spec sync, per-change failure handling and summary, and states the user-visible guarantees instead of the dispatch mechanism.

## ADDED Requirements

### Requirement: Bulk archive runs sequentially with spec sync and always completes

The plugin SHALL archive the selected changes one at a time in the listed order, applying spec sync first for each change that has delta specs, with the file work off the UI thread. Each row's status SHALL update in the open dialog as it progresses. When the run ends, successfully or not, the plugin SHALL post a summary and re-enable the dialog's OK button so the dialog can be closed.

#### Scenario: Successful batch archive
- **WHEN** the user confirms the bulk archive
- **THEN** each selected change SHALL be synced (if it has delta specs) and archived in order, and its row SHALL show "Archiving..." then "Done" while the dialog is open

#### Scenario: Partial failure
- **WHEN** one change fails to sync or archive
- **THEN** its row SHALL show "Failed" with the reason, and the remaining changes SHALL still be processed

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
