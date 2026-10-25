# CLI Update

## Purpose
Update action that runs `openspec update` to refresh agent instruction files, with CLI availability guards.

## Requirements

### Requirement: Update action triggers CLI update

The plugin SHALL provide an "Update OpenSpec…" action that runs `openspec update` in the background and displays the output in the OpenSpec console panel. When the detected CLI is 1.15.0 or later, the action SHALL first show the update preview and SHALL run `openspec update` only after the user applies it. Below 1.15.0 it SHALL run `openspec update` directly.

#### Scenario: Successful update
- **WHEN** the user triggers the Update OpenSpec action and the CLI is available, and on CLI 1.15.0+ the user has applied the preview
- **THEN** the plugin SHALL run `openspec update` via CliRunner, display stdout in the console panel, and show a success notification upon completion — unless the output reports pending legacy cleanup, in which case the review notice defined by the Legacy-cleanup outcome detection requirement replaces the bare success notification

#### Scenario: Update with errors
- **WHEN** `openspec update` exits with a non-zero exit code
- **THEN** the plugin SHALL display stderr in the console panel and show an error notification with the exit code

#### Scenario: Update progress
- **WHEN** the update command is running
- **THEN** the plugin SHALL show a background progress indicator with the label "Running openspec update"

#### Scenario: Older CLI runs the update directly
- **WHEN** the user triggers the Update OpenSpec action and the detected CLI is older than 1.15.0
- **THEN** the plugin SHALL run `openspec update` without a preview and SHALL NOT pass `--dry-run`, exactly as before this change

### Requirement: Update action CLI guard

The Update OpenSpec action SHALL be disabled when the OpenSpec CLI is not detected, with an explanatory tooltip.

#### Scenario: CLI not detected
- **WHEN** the action update cycle runs and CliDetectionService reports the CLI is unavailable
- **THEN** the action SHALL be disabled and its description SHALL indicate that the CLI is required

#### Scenario: CLI detected
- **WHEN** the action update cycle runs and CliDetectionService reports the CLI is available
- **THEN** the action SHALL be enabled with its standard description

#### Scenario: Non-OpenSpec project
- **WHEN** the current project is not an OpenSpec project
- **THEN** the action SHALL be hidden (not visible in menus)

### Requirement: Update action registration

The Update OpenSpec action SHALL be registered in plugin.xml within the OpenSpec main menu group and accessible from the tool window toolbar. Its text SHALL end with an ellipsis ("Update OpenSpec…"), because on CLI 1.15.0+ it opens a dialog.

#### Scenario: Menu placement
- **WHEN** the user opens the OpenSpec menu
- **THEN** "Update OpenSpec…" SHALL appear in the utility section after the Refresh action

#### Scenario: Toolbar placement
- **WHEN** the user views the OpenSpec tool window toolbar
- **THEN** an Update button SHALL be available in the toolbar group

### Requirement: Legacy-cleanup outcome detection

The plugin SHALL recognize when `openspec update` leaves legacy-file cleanup pending even though the CLI exits 0. On CLI 1.15.0+, when the applied plan's notes include `legacy-artifacts`, the plugin SHALL take the files to remove from the read-only `openspec update --dry-run --force --json` plan: its `delete` entries whose paths resolve inside the project. On older CLIs it SHALL parse the "Files to remove" block of the text output, keyed on the header and the bulleted entries, accepting and recording whatever guidance line the CLI prints. Both sources SHALL be contract-tested against captured real CLI output. The plugin SHALL NOT run `openspec update --force` itself.

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

### Requirement: Regeneration-loop recognition

Some CLI versions regenerate the very files their migration detector flags (observed on 1.4.1 and 1.5.0 for the junie integration: `init`/`update`/`--force` all re-create the flagged `.junie/commands/opsx-*.md` files), so cleanup cannot resolve the pending state. The plugin SHALL recognize this loop from its post-cleanup verification re-run and terminate the flow truthfully: explain that the CLI itself regenerates these files and nothing on the user's side needs fixing, auto-suppress the notice while the CLI reports the same set, and not offer deletion again for a set observed to regenerate.

#### Scenario: Cleanup verified successful
- **WHEN** the post-cleanup `openspec update` re-run reports no pending files (or a disjoint set)
- **THEN** the plugin SHALL report the cleanup as complete

#### Scenario: Regeneration loop detected
- **WHEN** the post-cleanup `openspec update` re-run reports the same file set that was just removed
- **THEN** the plugin SHALL explain that this CLI version regenerates the flagged files, SHALL auto-suppress the notice while the reported set is unchanged, and SHALL NOT offer deletion for that set again

#### Scenario: CLI change re-opens the flow
- **WHEN** a later `openspec update` (e.g. after a CLI upgrade) reports a pending set different from the recorded regenerating set
- **THEN** the cleanup flow SHALL be available again for the new set

### Requirement: Cleanup verification reports only what the CLI confirmed

After a legacy-file cleanup, the plugin SHALL report a clean state only when the verification re-run of `openspec update` succeeds and reports no files to remove. If the re-run exits non-zero, times out or cannot be started, the plugin SHALL say the cleanup could not be verified, and SHALL NOT report a clean state.

#### Scenario: Verification re-run fails
- **WHEN** the files are removed and the verification `openspec update` exits 1 with no stdout
- **THEN** the plugin SHALL report that the cleanup could not be verified, with the CLI's stderr when it has text, and SHALL NOT say the state is clean

#### Scenario: Verification re-run cannot start
- **WHEN** the verification re-run times out or the CLI is not found
- **THEN** the plugin SHALL report that the cleanup could not be verified

#### Scenario: Successful verification is unchanged
- **WHEN** the verification re-run exits 0 and reports no files to remove
- **THEN** the plugin SHALL report the clean state as before

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

### Requirement: A failed dry run is never "no changes"

The plugin SHALL treat the dry run as failed when it exits non-zero, when its JSON carries a `status[]` entry of severity `error`, or when its output isn't a JSON object with `changes`, `notes` and `failures` arrays. A failed dry run SHALL show the CLI's error message in the console and as a warning, SHALL offer nothing to apply, and SHALL NOT fall back to running `openspec update`.

#### Scenario: No OpenSpec directory
- **WHEN** the dry run exits 1 with empty arrays and `status:[{severity:"error",code:"update_error",…}]`
- **THEN** the plugin SHALL report the status message as a failure, not "already up to date"

#### Scenario: Unparseable output
- **WHEN** the dry run exits 0 but its stdout is not the plan object
- **THEN** the plugin SHALL report that the plan couldn't be read and SHALL NOT run `openspec update`

### Requirement: Every plugin-started update goes through the preview

On CLI 1.15.0+, the Update action, the prompt that follows a workflow-profile switch, and the Update button in Manage AI Tools SHALL all use the same preview-and-apply path. Because `openspec update` always covers every configured tool, a preview opened from one tool's button SHALL still show the whole plan.

#### Scenario: Profile switch
- **WHEN** a workflow-profile switch offers to run `openspec update` on CLI 1.15.0+
- **THEN** the plugin SHALL show the update preview in place of its Yes/No question, so applying the preview is the consent

#### Scenario: Manage AI Tools Update button
- **WHEN** the user clicks a tool's Update button in Manage AI Tools on CLI 1.15.0+
- **THEN** the plugin SHALL show the project-wide preview with that tool's group listed first and expanded

#### Scenario: Older CLI
- **WHEN** the detected CLI is older than 1.15.0
- **THEN** each entry point SHALL behave as before this change
