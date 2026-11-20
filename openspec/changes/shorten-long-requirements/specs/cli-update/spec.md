## MODIFIED Requirements

### Requirement: Legacy-cleanup outcome detection

The plugin SHALL recognize when `openspec update` leaves legacy cleanup pending although the CLI exits 0. On CLI 1.15.0 and later it SHALL take the files to remove from the read-only `openspec update --dry-run --force --json` plan, and on older CLIs from the "Files to remove" block of the text output. Both sources SHALL be contract-tested against captured CLI output. The plugin SHALL NOT run `openspec update --force` itself.

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

#### Scenario: Legacy files from the forced dry-run plan
- **WHEN** the CLI is 1.15.0+ and the applied plan's notes include `legacy-artifacts`
- **THEN** the plugin SHALL run `openspec update --dry-run --force --json` and offer its in-project `delete` entries in the cleanup flow, with the note's message as the guidance line
- **AND** it SHALL NOT offer an entry outside the project, and SHALL NOT run `--force` without `--dry-run`

#### Scenario: The forced dry run fails
- **WHEN** the forced dry run exits non-zero, reports a `status[]` error, or prints output that isn't valid JSON
- **THEN** the plugin SHALL report that the cleanup list couldn't be read and SHALL offer no files

#### Scenario: Plan sources
- **WHEN** the applied plan's notes include `legacy-artifacts` on CLI 1.15.0 or later
- **THEN** the files to remove SHALL be the plan's `delete` entries whose paths resolve inside the project
- **AND** on older CLIs the plugin SHALL parse the header and bulleted entries of the text output, accepting and recording the guidance line the CLI prints

### Requirement: Consented surgical cleanup

The plugin SHALL resolve pending legacy cleanup only with explicit user consent, deleting only files that appear in the CLI's own files-to-remove list, that the user has checked, that exist on disk, and that resolve inside the project root. The plugin SHALL NOT invoke `openspec update --force` on the user's behalf. After a cleanup it SHALL re-run `openspec update` and report the resulting state.

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

#### Scenario: Undoable deletion
- **WHEN** files are deleted
- **THEN** deletion SHALL run through the IDE's VFS inside a single undoable write command

### Requirement: Regeneration-loop recognition

The plugin SHALL recognize a regeneration loop, where the CLI recreates the files its migration detector flags, from its post-cleanup verification re-run. It SHALL explain that the CLI regenerates these files and that nothing on the user's side needs fixing, suppress the notice while the CLI reports the same set, and not offer deletion again for a set observed to regenerate.

#### Scenario: Cleanup verified successful
- **WHEN** the post-cleanup `openspec update` re-run reports no pending files (or a disjoint set)
- **THEN** the plugin SHALL report the cleanup as complete

#### Scenario: Regeneration loop detected
- **WHEN** the post-cleanup `openspec update` re-run reports the same file set that was just removed
- **THEN** the plugin SHALL explain that this CLI version regenerates the flagged files, SHALL auto-suppress the notice while the reported set is unchanged, and SHALL NOT offer deletion for that set again

#### Scenario: CLI change re-opens the flow
- **WHEN** a later `openspec update` (e.g. after a CLI upgrade) reports a pending set different from the recorded regenerating set
- **THEN** the cleanup flow SHALL be available again for the new set

#### Scenario: Known regenerating set
- **WHEN** CLI 1.4.1 or 1.5.0 is used with the junie integration
- **THEN** `init`, `update` and `--force` each re-create the flagged `.junie/commands/opsx-*.md` files, so cleanup cannot resolve the pending state
