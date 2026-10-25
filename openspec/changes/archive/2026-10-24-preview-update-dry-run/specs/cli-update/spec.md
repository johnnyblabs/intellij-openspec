## MODIFIED Requirements

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

## ADDED Requirements

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
