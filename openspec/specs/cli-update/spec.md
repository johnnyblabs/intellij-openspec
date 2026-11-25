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
- **THEN** the files to remove SHALL be the plan's `delete` entries of tool `openspec` whose paths resolve inside the project, other than files in OpenSpec skill folders and the shared-skills marker
- **AND** on older CLIs the plugin SHALL parse the header and bulleted entries of the text output, accepting and recording the guidance line the CLI prints

#### Scenario: Other deletes are not legacy files
- **WHEN** the forced plan also deletes files for another tool, such as stale skills or files left by an earlier release (`earlier-release`)
- **THEN** the plugin SHALL NOT offer those files in the cleanup flow

#### Scenario: Skill folders are not legacy files
- **WHEN** on CLI 2.1 the forced plan deletes, with tool `openspec`, a tool-folder skill copy such as `.cursor/skills/openspec-explore/SKILL.md`, a file beside it, a shared skill, or `.agents/skills/.openspec-target`
- **THEN** the plugin SHALL NOT offer that file in the cleanup flow, because the update itself manages it

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
- **THEN** the preview SHALL show a summary of the changes by action, the notes, any failures, and the changes grouped by tool with deletes listed first, except that a migration plan groups its shared-skill, replaced-copy and command-file changes across tools

#### Scenario: Legacy tool directory move is flagged
- **WHEN** the plan's notes include `legacy-tool-directories`
- **THEN** the preview SHALL show that note above the changes as a warning, with its message as the CLI printed it

#### Scenario: Notes that reach beyond the project or replace edits
- **WHEN** the plan's notes include `global-skills`, `removed-skills` or `edited-skills`
- **THEN** the preview SHALL show each such note above the changes as a warning, with its message as the CLI printed it

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
- **WHEN** a note's code is none of `legacy-tool-directories`, `legacy-artifacts`, `global-skills`, `removed-skills` or `edited-skills`
- **THEN** the preview SHALL still show the note's message

#### Scenario: Unedited legacy files the update removes unasked
- **WHEN** on CLI 1.17.0 or later the plan deletes unedited legacy files and has no `legacy-artifacts` note
- **THEN** the preview SHALL list those deletes in their tool's group like any other delete, and Cancel SHALL be the default button

#### Scenario: Files left by an earlier release
- **WHEN** a change's tool is `earlier-release`
- **THEN** the preview SHALL label its group as files left by an earlier OpenSpec release instead of showing the raw tool value, and the group SHALL keep its change count

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

### Requirement: The update preview presents the OpenSpec 2 migration

On CLI 2.0.0 or later, when the plan adds or updates `.agents/skills/.openspec-target` and deletes a tool-folder skill copy or a 1.x command file, the preview SHALL say the update migrates the project to OpenSpec 2. It SHALL group the shared-skill changes, the replaced tool-folder skill copies and the command-file deletes, each as one group regardless of tool, and SHALL show each tool-folder skill link as a link to its shared skill.

#### Scenario: Migration plan from a 1.x project
- **WHEN** the dry run of a project set up by CLI 1.17.0 for Claude and Cursor runs on CLI 2.0.0, 2.1.0 or 2.1.1
- **THEN** the preview SHALL show the migration banner
- **AND** it SHALL list the shared skills, the replaced skill copies and the command files each in its own group, whichever tool value the CLI gave the changes

#### Scenario: A 1.x project that already had a shared folder
- **WHEN** a project set up by CLI 1.17.0 for Claude and Codex is previewed on a 2.x CLI, whose plan updates the existing `.agents/skills/.openspec-target`
- **THEN** the preview SHALL show the migration banner

#### Scenario: Nested command folders
- **WHEN** the plan deletes `.claude/commands/opsx/apply.md` or `.gemini/commands/opsx/apply.toml`
- **THEN** the preview SHALL list it in the command-files group

#### Scenario: A tool-folder link
- **WHEN** the plan adds a tool-folder entry such as `.claude/skills/openspec-explore` with no file extension
- **THEN** the preview SHALL show it as a link to `.agents/skills/openspec-explore`

#### Scenario: Not a migration
- **WHEN** the plan neither adds nor updates `.agents/skills/.openspec-target`, or deletes no tool-folder skill copy or command file
- **THEN** the preview SHALL show no migration banner and SHALL group changes by tool as before

#### Scenario: A 1.x CLI moving a former tool folder
- **WHEN** CLI 1.17.0 plans to move a 1.6-era Antigravity project's skills from `.agent/` to `.agents/`, adding the marker and deleting the old copies
- **THEN** the preview SHALL show no migration banner, because the update doesn't move the project to OpenSpec 2

### Requirement: Files OpenSpec can't confirm as its own are named before the update removes them

When the plan deletes or updates a file in an OpenSpec skill folder that OpenSpec can't confirm it generated, the preview SHALL name each such file and warn that the update deletes or overwrites it and keeps no copy. Apply SHALL stay disabled until the user confirms they have backed the files up. The warning SHALL depend on the plan alone, not on whether it is a migration.

#### Scenario: Edited copy on CLI 2.1
- **WHEN** a 1.17.0 project's `.claude/skills/openspec-explore/SKILL.md` has a line the user added and the CLI is 2.1.0 or 2.1.1
- **THEN** the preview SHALL name that file in the warning and SHALL keep Apply disabled until the backed-up confirmation is ticked

#### Scenario: Edited copy on CLI 2.0.0
- **WHEN** the same project is previewed on CLI 2.0.0, whose plan doesn't delete the edited copy
- **THEN** the preview SHALL show no warning

#### Scenario: A project CLI 2.0.0 already migrated
- **WHEN** CLI 2.0.0 kept an edited `.cursor/skills/openspec-explore/SKILL.md` and a 2.1 plan now deletes it
- **THEN** the preview SHALL name that file in the warning

#### Scenario: A file beside a skill
- **WHEN** the plan deletes `notes.md` in a tool folder's `openspec-explore` skill folder
- **THEN** the preview SHALL name that file in the warning, while operating-system junk files SHALL NOT be named

#### Scenario: An overwritten copy
- **WHEN** on CLI 2.1 the plan updates an edited copy that the CLI writes in place of a link, such as Eve's `agent/skills/openspec-explore/SKILL.md`
- **THEN** the preview SHALL name that file in the warning

#### Scenario: Unedited files
- **WHEN** every file the plan deletes or updates in a skill folder is a released OpenSpec file, or still equals the project's shared copy apart from upstream's per-tool front-matter difference
- **THEN** the preview SHALL show no warning, and Apply SHALL depend only on the plan's failures

#### Scenario: Unreadable file
- **WHEN** a skill-folder file the plan deletes can't be read
- **THEN** the preview SHALL name it in the warning

### Requirement: What an applied update kept or replaced is reported

After an applied `openspec update`, the plugin SHALL read the run's text output for the files it kept because they differ from what OpenSpec generated, the files it left because it couldn't remove them, and the skills whose edits it replaced. When there are any, it SHALL notify the user, listing each path, as a link where it is in the project, with the CLI's guidance. Output it doesn't recognize SHALL yield no report.

#### Scenario: Edited command file kept
- **WHEN** the applied run prints `Kept 1 command file that does not match what OpenSpec generated (likely edited):` followed by `.claude/commands/opsx/explore.md`
- **THEN** the plugin SHALL notify the user with that path as a link and the guidance line the CLI printed

#### Scenario: Edited skill folders kept on 2.0.0
- **WHEN** the applied run prints `Kept 2 skill folders with your own changes: .claude/skills/openspec-explore, .cursor/skills/openspec-explore. …`
- **THEN** the notification SHALL list both folders

#### Scenario: Edits replaced on 2.0.0
- **WHEN** the applied run prints `Replaced your edits in 1 skill OpenSpec installs: …`
- **THEN** the notification SHALL show that report as a warning with the skills it names

#### Scenario: Nothing kept
- **WHEN** the applied run's output reports nothing kept, left or replaced
- **THEN** the plugin SHALL show no such notification

### Requirement: A 1.x project is told once to migrate

When the detected CLI is 2.0.0 or later and an OpenSpec project holds a skill copy that a 1.x release generated, unedited, not a link and with nothing of the user's beside it, in a tool folder other than `.agents`, the plugin SHALL notify once per project that Update migrates it to OpenSpec 2. The notice SHALL offer an action that opens the update preview, and SHALL NOT be shown again for that project.

#### Scenario: 1.x project with a 2.x CLI
- **WHEN** a project set up by CLI 1.17.0 opens with CLI 2.1.1 detected
- **THEN** the plugin SHALL show the migration notice, and its Update action SHALL open the update preview

#### Scenario: Already migrated
- **WHEN** a 2.x update has replaced the project's tool-folder copies, including one where CLI 2.0.0 kept edited copies or a skill folder holding the user's notes
- **THEN** the plugin SHALL NOT show the migration notice

#### Scenario: 1.x CLI
- **WHEN** the detected CLI is older than 2.0.0, or no CLI is detected
- **THEN** the plugin SHALL NOT show the migration notice

#### Scenario: Shown once
- **WHEN** the notice has been shown for a project
- **THEN** the plugin SHALL NOT show it again for that project

### Requirement: A failed update on 2.1.0 names Git and the network

When an applied `openspec update` fails on detected CLI 2.1.0 and its output says Git is needed or the skills couldn't be fetched, the plugin's failure report SHALL add that this release installs skills with Git over the network, and that 2.1.1 installs offline. Other failures and other releases' reports SHALL be unchanged.

#### Scenario: No Git on 2.1.0
- **WHEN** the applied update on CLI 2.1.0 exits 1 reporting that Git is needed
- **THEN** the failure report SHALL include the Git and network sentence

#### Scenario: Another failure or release
- **WHEN** an applied update fails on CLI 2.1.0 for another reason, or fails on CLI 2.1.1 or 2.0.0
- **THEN** the failure report SHALL NOT include that sentence

### Requirement: CLI runs never wait for input

Every CLI command the plugin runs in the background SHALL be started non-interactively (`OPEN_SPEC_INTERACTIVE=0`) with its standard input closed, so no prompt can hold a run. A command the plugin hands to the IDE terminal stays interactive; on CLI 2.1.0 or later the hint shown with that handoff SHALL say the CLI may ask where to keep the project's skills or whether to set skills up for the machine.

#### Scenario: Background run
- **WHEN** the plugin runs any CLI command in the background
- **THEN** the process SHALL have `OPEN_SPEC_INTERACTIVE=0` in its environment and a closed standard input

#### Scenario: Terminal handoff on 2.1
- **WHEN** the user chooses to run `openspec update` in the terminal on CLI 2.1.0 or later
- **THEN** the hint SHALL say the CLI may ask where to keep the project's skills or whether to set skills up for the machine
