## MODIFIED Requirements

### Requirement: Archive action

The plugin SHALL archive completed changes, through the OpenSpec CLI when it is available (see "Archive delegates to the OpenSpec CLI") and otherwise itself, with post-archive commit, push, and tracker updates. A Bulk Archive option SHALL be available when multiple active changes exist. When the change contains unsynced delta specs, the plugin SHALL prompt the user before proceeding with a three-option dialog. The archive SHALL run in a background task, never on the UI thread, and the post-archive UI updates (popover, refresh, notifications) SHALL be dispatched to the UI thread with `invokeLater`.

#### Scenario: Archive flow
- **WHEN** the user archives a change
- **THEN** the change SHALL be moved to the archive directory and post-archive steps SHALL execute

#### Scenario: Bulk archive entry point
- **WHEN** multiple active changes exist
- **THEN** a Bulk Archive action SHALL be available in the menu and toolbar

#### Scenario: Archive guard with unsynced delta specs
- **WHEN** the user clicks Archive and the change has unsynced delta specs (`hasDeltaSpecs` is true)
- **THEN** the plugin SHALL display a confirmation dialog with three options: "Sync First", "Archive Without Syncing", and "Cancel"

#### Scenario: Archive guard — Sync First
- **WHEN** the user selects "Sync First" from the archive guard dialog
- **THEN** the plugin SHALL trigger the sync specs workflow and SHALL NOT proceed with archiving

#### Scenario: Archive guard — Archive Without Syncing
- **WHEN** the user selects "Archive Without Syncing" from the archive guard dialog
- **THEN** the plugin SHALL proceed with archiving the change as-is, including the unsynced delta specs, without updating main specs (`--skip-specs` when the CLI archives)

#### Scenario: Archive guard — Cancel
- **WHEN** the user selects "Cancel" from the archive guard dialog
- **THEN** the plugin SHALL abort the archive action and take no further action

#### Scenario: Archive without delta specs
- **WHEN** the user clicks Archive and the change has no delta specs (`hasDeltaSpecs` is false)
- **THEN** the plugin SHALL proceed directly with archiving without showing the guard dialog

#### Scenario: No invokeAndWait in archive background task
- **WHEN** the background task executes the archive operation
- **THEN** the archive SHALL run on the background task's thread, and the popover and refresh SHALL be chained in a single `invokeLater` to the UI thread, never `invokeAndWait`

#### Scenario: Archive error reported from invokeLater
- **WHEN** the archive fails or is refused
- **THEN** the failure SHALL be reported from an `invokeLater` on the UI thread: the archive button SHALL be re-enabled and an error notification SHALL be shown

### Requirement: Archive target directory

When the plugin archives a change itself (no OpenSpec CLI, or a CLI older than 1.5), it SHALL move the change to `openspec/changes/archive/YYYY-MM-DD-<name>/`, using the local date and keeping a name that already starts with a date unchanged. It SHALL refuse, before writing any spec, when that target already exists. It SHALL create `openspec/changes/archive/` when missing and SHALL NOT create `openspec/archive/`, though it SHALL still read archived changes from an existing legacy `openspec/archive/`.

#### Scenario: Dated archive name
- **WHEN** the user archives change `add-login` on 2026-10-17
- **THEN** the change SHALL be moved to `openspec/changes/archive/2026-10-17-add-login/`

#### Scenario: Target already exists
- **WHEN** `openspec/changes/archive/2026-10-17-add-login/` already exists
- **THEN** the archive SHALL be refused with no spec written and the change left active

#### Scenario: No archive directory yet
- **WHEN** `openspec/changes/archive/` does not exist
- **THEN** the plugin SHALL create it and archive into it, and SHALL NOT create `openspec/archive/`

## ADDED Requirements

### Requirement: Archive delegates to the OpenSpec CLI

With OpenSpec CLI 1.5 or later available, the plugin SHALL archive by running `openspec archive <name> --yes --json`, adding `--skip-specs` when the user archives without syncing. It SHALL report the archived folder on success, and on a non-zero exit SHALL show the CLI's `status[].message` and `fix` and leave the change active. Without a CLI, or with an older one, the plugin SHALL archive itself.

#### Scenario: CLI archives the change
- **WHEN** OpenSpec CLI 1.14 is available and the user archives `add-login`
- **THEN** the plugin SHALL run `openspec archive add-login --yes --json` and report the folder named in its `archivedAs`

#### Scenario: CLI refuses the change
- **WHEN** the CLI exits non-zero with a `status` entry
- **THEN** the plugin SHALL show that entry's `message` and `fix`, and the change SHALL stay active

#### Scenario: Older or missing CLI
- **WHEN** no OpenSpec CLI is available, or its version is below 1.5
- **THEN** the plugin SHALL archive the change itself, as described in "Archive target directory"
