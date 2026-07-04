# Delta — cli-update

## ADDED Requirements

### Requirement: Legacy-cleanup outcome detection

The plugin SHALL recognize when `openspec update` reports pending legacy-file cleanup (the skills-migration "Files to remove" block with `--force`/interactive guidance) even though the CLI exits 0, by parsing the command output. Parsing SHALL be covered by a contract test against captured real CLI output, and an output without the migration block SHALL change nothing about the existing Update flow.

#### Scenario: Legacy files pending
- **WHEN** `openspec update` output contains the migration block listing files to remove
- **THEN** the plugin SHALL treat the update as incomplete-with-pending-cleanup and offer the cleanup flow instead of reporting bare success

#### Scenario: No migration block
- **WHEN** `openspec update` output contains no migration block
- **THEN** the Update action SHALL behave exactly as before this change

#### Scenario: Partially recognizable block
- **WHEN** the migration block is present but only some file entries parse
- **THEN** the plugin SHALL offer only the parsed files — degradation reduces the offered set, never expands it

### Requirement: Consented surgical cleanup

The plugin SHALL resolve pending legacy cleanup through explicit user consent, deleting only files that appear in the CLI's own files-to-remove list, are checked by the user, exist on disk, and resolve inside the project root. Deletion SHALL run through the IDE's VFS inside a single undoable write command. The plugin SHALL NOT invoke `openspec update --force` on the user's behalf. After a cleanup, the plugin SHALL re-run `openspec update` and report the resulting state.

#### Scenario: Review and remove
- **WHEN** the user opens the cleanup review from the Update notification
- **THEN** the dialog SHALL list each pending file as an openable link with a checkbox (all checked by default) and SHALL quote the CLI's no-user-content statement

#### Scenario: Surgical deletion
- **WHEN** the user confirms Remove selected
- **THEN** the plugin SHALL delete exactly the checked, existing, project-contained files in one undoable write command and then re-run `openspec update`

#### Scenario: Out-of-scope path discarded
- **WHEN** a listed path does not exist on disk or resolves outside the project root
- **THEN** the plugin SHALL exclude it from the deletion set

#### Scenario: No force on the user's behalf
- **WHEN** the cleanup flow runs, in any state
- **THEN** the plugin SHALL NOT invoke `openspec update --force`

### Requirement: Cleanup escape hatches

The cleanup flow SHALL preserve non-destructive exits: a terminal handoff for the CLI's own interactive flow, and a dismissal that suppresses the notice only while the pending file set is unchanged.

#### Scenario: Terminal handoff
- **WHEN** the user chooses to run interactively
- **THEN** the plugin SHALL open the project terminal via the OpenSpec terminal launcher with the `openspec update` command prepared for the user to run

#### Scenario: Dismissal without nagging
- **WHEN** the user chooses Not now
- **THEN** subsequent Update runs SHALL NOT re-raise the cleanup notice while the CLI reports the same pending file set

#### Scenario: Pending set changes after dismissal
- **WHEN** a later `openspec update` reports a different pending file set than the dismissed one
- **THEN** the cleanup notice SHALL be offered again
