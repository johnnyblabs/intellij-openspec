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
- **THEN** the plugin SHALL run `openspec update --dry-run --force --json` and offer the in-project `delete` entries of OpenSpec's own legacy cleanup (tool `openspec`) in the cleanup flow, with the note's message as the guidance line
- **AND** it SHALL NOT offer an entry outside the project, and SHALL NOT run `--force` without `--dry-run`

#### Scenario: The forced dry run fails
- **WHEN** the forced dry run exits non-zero, reports a `status[]` error, or prints output that isn't valid JSON
- **THEN** the plugin SHALL report that the cleanup list couldn't be read and SHALL offer no files

#### Scenario: Plan sources
- **WHEN** the applied plan's notes include `legacy-artifacts` on CLI 1.15.0 or later
- **THEN** the files to remove SHALL be the plan's `delete` entries of tool `openspec` whose paths resolve inside the project
- **AND** on older CLIs the plugin SHALL parse the header and bulleted entries of the text output, accepting and recording the guidance line the CLI prints

#### Scenario: Other deletes are not legacy files
- **WHEN** the forced plan also deletes files for another tool, such as stale skills or files left by an earlier release (`earlier-release`)
- **THEN** the plugin SHALL NOT offer those files in the cleanup flow

### Requirement: Regeneration-loop recognition

The plugin SHALL recognize a regeneration loop, where the CLI recreates the files its migration detector flags, from a post-cleanup re-run of `openspec update` (CLIs below 1.15.0). A read-only verification SHALL NOT count as a loop. It SHALL explain that the CLI regenerates these files and that nothing on the user's side needs fixing, suppress the notice while the CLI reports the same set, and not offer deletion again for a set observed to regenerate.

#### Scenario: Cleanup verified successful
- **WHEN** the post-cleanup verification reports no pending files (or a disjoint set)
- **THEN** the plugin SHALL report the cleanup as complete

#### Scenario: Regeneration loop detected
- **WHEN** the post-cleanup re-run reports the same file set that was just removed
- **THEN** the plugin SHALL explain that this CLI version regenerates the flagged files, SHALL auto-suppress the notice while the reported set is unchanged, and SHALL NOT offer deletion for that set again

#### Scenario: CLI change re-opens the flow
- **WHEN** a later `openspec update` (e.g. after a CLI upgrade) reports a pending set different from the recorded regenerating set
- **THEN** the cleanup flow SHALL be available again for the new set

#### Scenario: A read-only verification is never a loop
- **WHEN** a read-only verification still lists files that were in the set the user was offered
- **THEN** the plugin SHALL report them as still pending, SHALL NOT say the CLI regenerated them, and SHALL NOT record them as a regenerating set

#### Scenario: Known regenerating set
- **WHEN** CLI 1.4.1 or 1.5.0 is used with the junie integration
- **THEN** `init`, `update` and `--force` each re-create the flagged `.junie/commands/opsx-*.md` files, so cleanup cannot resolve the pending state

### Requirement: Cleanup verification reports only what the CLI confirmed

After a legacy-file cleanup, the plugin SHALL verify on CLI 1.15.0+ with `openspec update --dry-run --json`, which writes nothing, and below 1.15.0 with a re-run of `openspec update`. It SHALL report a clean state only when the verification succeeds and reports no files to remove. If it fails, times out or cannot start, the plugin SHALL say the cleanup could not be verified and SHALL NOT report a clean state.

#### Scenario: Verification re-run fails
- **WHEN** the files are removed and the verification exits 1 with no stdout
- **THEN** the plugin SHALL report that the cleanup could not be verified, with the CLI's stderr when it has text, and SHALL NOT say the state is clean

#### Scenario: Verification re-run cannot start
- **WHEN** the verification times out or the CLI is not found
- **THEN** the plugin SHALL report that the cleanup could not be verified

#### Scenario: Successful verification is unchanged
- **WHEN** the verification exits 0 and reports no files to remove
- **THEN** the plugin SHALL report the clean state as before

#### Scenario: Verification on CLI 1.15 or later writes nothing
- **WHEN** the user removes legacy files on CLI 1.15.0 or later
- **THEN** the plugin SHALL verify with `openspec update --dry-run --json` and SHALL NOT run `openspec update` without `--dry-run` for the verification
- **AND** a plan that has no `legacy-artifacts` note SHALL count as no files to remove

#### Scenario: Files still pending after a read-only verification
- **WHEN** the verification plan still has the `legacy-artifacts` note
- **THEN** the plugin SHALL take the pending files from `openspec update --dry-run --force --json` in the same way as when the cleanup was first offered, and SHALL report them as still pending

#### Scenario: Finishing the update goes through the preview
- **WHEN** a read-only verification finds no `legacy-artifacts` note but its plan still has changes, notes or failures
- **THEN** the plugin SHALL offer to run Update, and accepting SHALL open the update preview before anything changes
- **AND** the plugin SHALL NOT report a clean state for that plan

#### Scenario: A remaining note with nothing listed
- **WHEN** the verification plan still has the `legacy-artifacts` note but the forced plan lists no legacy files to remove
- **THEN** the plugin SHALL say that OpenSpec still reports legacy files, and SHALL NOT report the cleanup as complete

### Requirement: Update preview

On CLI 1.15.0+, before any plugin-started `openspec update`, the plugin SHALL run `openspec update --dry-run --json` off the UI thread and SHALL show its plan in a modal preview. The plan SHALL be held only for that preview, never persisted. Both the dry run and the applied run SHALL be started non-interactively (`OPEN_SPEC_INTERACTIVE=0`), so the applied run takes the path the preview showed.

#### Scenario: Plan shown before anything changes
- **WHEN** the dry run returns a plan with changes, notes or failures
- **THEN** the preview SHALL show a summary of the changes by action, the notes, any failures, and the changes grouped by tool with deletes listed first

#### Scenario: Legacy tool directory move is flagged
- **WHEN** the plan's notes include `legacy-tool-directories`
- **THEN** the preview SHALL show that note above the changes as a warning, with its message as the CLI printed it

#### Scenario: Entries outside the project
- **WHEN** a change's path starts with `~/` or is absolute
- **THEN** the preview SHALL list it in a separate "Outside this project" group, never mixed into a tool's in-project changes

#### Scenario: Apply runs the update once
- **WHEN** the user applies the preview
- **THEN** the plugin SHALL run `openspec update` (without `--dry-run`, `--json` or `--force`) once, and SHALL NOT run another dry run for it

#### Scenario: Cancel changes nothing
- **WHEN** the user cancels the preview
- **THEN** the plugin SHALL run no further CLI command and SHALL change no file

#### Scenario: Deletes make Cancel the default
- **WHEN** the plan contains a `delete` change
- **THEN** Cancel SHALL be the preview's default button

#### Scenario: Failures block Apply
- **WHEN** the plan's `failures` is non-empty
- **THEN** the preview SHALL list each failure with its tool and error, disable Apply, and explain that `openspec update` stops at the first failing tool

#### Scenario: Nothing to update
- **WHEN** the plan's changes, notes and failures are all empty
- **THEN** the plugin SHALL report that the project is already up to date, SHALL NOT show the preview, and SHALL NOT run `openspec update`

#### Scenario: Unknown note codes
- **WHEN** a note's code is neither `legacy-tool-directories` nor `legacy-artifacts`
- **THEN** the preview SHALL still show the note's message

#### Scenario: Unedited legacy files the update removes unasked
- **WHEN** on CLI 1.17.0 or later the plan deletes unedited legacy files and has no `legacy-artifacts` note
- **THEN** the preview SHALL list those deletes in their tool's group like any other delete, and Cancel SHALL be the default button

#### Scenario: Files left by an earlier release
- **WHEN** a change's tool is `earlier-release`
- **THEN** the preview SHALL label its group as files left by an earlier OpenSpec release instead of showing the raw tool value, and the group SHALL keep its change count
