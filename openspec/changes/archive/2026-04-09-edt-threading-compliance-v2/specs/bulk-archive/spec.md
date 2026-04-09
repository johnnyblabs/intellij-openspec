## MODIFIED Requirements

### Requirement: Sequential archive with spec sync

The plugin SHALL archive selected changes sequentially in the listed order, applying spec sync for each change that has delta specs. Each archive operation SHALL be dispatched to the EDT via `invokeLater` (since `ChangeService.archiveChange` requires `WriteAction`). The background task SHALL track completion of all archives via an `AtomicInteger` counter and post the summary notification only after all archives complete. The dialog's OK button SHALL remain disabled until all archives finish.

#### Scenario: Successful batch archive
- **WHEN** the user confirms the bulk archive
- **THEN** each selected change SHALL be synced (if it has delta specs) and archived via `invokeLater` dispatch, with per-change progress reporting via table model updates

#### Scenario: Partial failure
- **WHEN** one change fails to archive inside the `invokeLater` block
- **THEN** the plugin SHALL catch the exception, update that row's status to "Failed", and continue processing remaining changes

#### Scenario: Archive order determines sync priority
- **WHEN** two changes have conflicting delta specs for the same requirement
- **THEN** the last-archived change's spec sync SHALL take precedence (last write wins)

#### Scenario: No invokeAndWait in archive loop
- **WHEN** the background task iterates over selected changes to archive
- **THEN** each `changeService.archiveChange` call SHALL be dispatched via `invokeLater`, NOT `invokeAndWait`

#### Scenario: Completion tracking
- **WHEN** all archive `invokeLater` blocks have executed
- **THEN** the background task SHALL post a summary notification with the count of successful archives and re-enable the OK button
