## MODIFIED Requirements

### Requirement: Legacy-cleanup outcome detection

The plugin SHALL recognize when `openspec update` reports pending legacy-file cleanup (the skills-migration "Files to remove" block with `--force`/interactive guidance) even though the CLI exits 0, by parsing the command output. Recognition SHALL key on the "Files to remove" header and the bulleted file entries, accept whatever guidance line the CLI prints between them, and record that line. Parsing SHALL be covered by contract tests against captured real CLI output from every wording in the supported range, and an output without the migration block SHALL change nothing about the existing Update flow.

#### Scenario: Legacy files pending
- **WHEN** `openspec update` output contains the migration block listing files to remove
- **THEN** the plugin SHALL treat the update as incomplete-with-pending-cleanup and offer the cleanup flow instead of reporting bare success

#### Scenario: Guidance line reworded by the CLI
- **WHEN** the migration block's guidance line is the 1.14+ "These files will be deleted entirely. Back up any custom content before proceeding:" rather than the earlier "No user content to preserve:"
- **THEN** the plugin SHALL still recognize the block, offer the same files, and record the line it printed

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
- **THEN** the dialog SHALL list each pending file as an openable link with a checkbox and SHALL quote the guidance line the CLI printed; files SHALL start checked only when the CLI certified they hold no user content, and unchecked when it warned to back up custom content

#### Scenario: Surgical deletion
- **WHEN** the user confirms Remove selected
- **THEN** the plugin SHALL delete exactly the checked, existing, project-contained files in one undoable write command and then re-run `openspec update`

#### Scenario: Out-of-scope path discarded
- **WHEN** a listed path does not exist on disk or resolves outside the project root
- **THEN** the plugin SHALL exclude it from the deletion set

#### Scenario: No force on the user's behalf
- **WHEN** the cleanup flow runs, in any state
- **THEN** the plugin SHALL NOT invoke `openspec update --force`
