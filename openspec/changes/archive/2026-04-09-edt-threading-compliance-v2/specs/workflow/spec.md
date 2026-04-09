## MODIFIED Requirements

### Requirement: Archive action

The plugin SHALL move completed changes to `openspec/changes/archive/YYYY-MM-DD-<name>/` with post-archive commit, push, and tracker updates. A Bulk Archive option SHALL be available when multiple active changes exist. When the change contains unsynced delta specs, the plugin SHALL prompt the user before proceeding with a three-option dialog. The archive operation in `WorkflowActionPanel` SHALL dispatch `changeService.archiveChange` via `invokeLater` from the background task, NOT via `invokeAndWait`. Post-archive UI updates (popover, refresh) SHALL execute inside the same `invokeLater` block.

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
- **THEN** the plugin SHALL proceed with archiving the change as-is, including the unsynced delta specs

#### Scenario: Archive guard — Cancel
- **WHEN** the user selects "Cancel" from the archive guard dialog
- **THEN** the plugin SHALL abort the archive action and take no further action

#### Scenario: Archive without delta specs
- **WHEN** the user clicks Archive and the change has no delta specs (`hasDeltaSpecs` is false)
- **THEN** the plugin SHALL proceed directly with archiving without showing the guard dialog

#### Scenario: No invokeAndWait in archive background task
- **WHEN** the background task executes the archive operation
- **THEN** `changeService.archiveChange` SHALL be dispatched via `invokeLater`, and the popover and refresh SHALL chain inside the same `invokeLater` lambda

#### Scenario: Archive error reported from invokeLater
- **WHEN** `changeService.archiveChange` throws inside the `invokeLater` block
- **THEN** the exception SHALL be caught, the archive button SHALL be re-enabled, and an error notification SHALL be shown

### Requirement: Propose action

The plugin SHALL create a new change with all required artifacts (proposal.md, design.md, tasks.md, specs/) via built-in scaffolding matching the OpenSpec 1.2.0 template structure. The `createChange` call and subsequent UI updates SHALL execute via `invokeLater` from a pooled thread to avoid blocking the EDT. When multiple schemas are available, the propose dialog SHALL include a schema selector and pass the selected schema to the CLI.

#### Scenario: Change creation
- **WHEN** the user proposes a change
- **THEN** the plugin SHALL dispatch to a pooled thread, execute `createChange` via `invokeLater` (since it requires WriteAction), and chain the tool window refresh and auto-focus inside the same EDT dispatch

#### Scenario: Schema selection during propose
- **WHEN** multiple schemas are available and the user proposes a change
- **THEN** the ProposeChangeDialog SHALL display a schema combo box and pass `--schema "<schema>"` to the `openspec new change` command
