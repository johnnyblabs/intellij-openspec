## ADDED Requirements

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

When the detected CLI is 2.0.0 or later and an OpenSpec project holds a skill copy that a 1.x release generated, unedited and not a link, in a tool folder other than `.agents`, the plugin SHALL notify once per project that Update migrates it to OpenSpec 2. The notice SHALL offer an action that opens the update preview, and SHALL NOT be shown again for that project.

#### Scenario: 1.x project with a 2.x CLI
- **WHEN** a project set up by CLI 1.17.0 opens with CLI 2.1.1 detected
- **THEN** the plugin SHALL show the migration notice, and its Update action SHALL open the update preview

#### Scenario: Already migrated
- **WHEN** a 2.x update has replaced the project's tool-folder copies, including one where CLI 2.0.0 kept edited copies
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

## MODIFIED Requirements

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
