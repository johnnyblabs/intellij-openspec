## MODIFIED Requirements

### Requirement: Bulk archive runs sequentially with spec sync and always completes

The plugin SHALL archive the selected changes one at a time in the listed order, off the UI thread. With OpenSpec CLI 1.5 or later it SHALL sync and archive each change in one `openspec archive <name> --yes --json` call; otherwise it SHALL apply spec sync for each change that has delta specs, then archive it itself. Each row's status SHALL update as the run progresses. When the run ends, successfully or not, the plugin SHALL post a summary and re-enable the OK button so the dialog can close.

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
