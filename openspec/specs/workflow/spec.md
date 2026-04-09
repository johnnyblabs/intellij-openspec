# Workflow

## Purpose
Artifact generation pipeline: propose, generate, apply, archive actions with delivery routing and progress feedback.
## Requirements
### Requirement: Propose action

The plugin SHALL create a new change with all required artifacts (proposal.md, design.md, tasks.md, specs/) via built-in scaffolding matching the OpenSpec 1.2.0 template structure. The `createChange` call and subsequent UI updates SHALL execute via `invokeLater` from a pooled thread to avoid blocking the EDT. When multiple schemas are available, the propose dialog SHALL include a schema selector and pass the selected schema to the CLI.

#### Scenario: Change creation
- **WHEN** the user proposes a change
- **THEN** the plugin SHALL dispatch to a pooled thread, execute `createChange` via `invokeLater` (since it requires WriteAction), and chain the tool window refresh and auto-focus inside the same EDT dispatch

#### Scenario: Schema selection during propose
- **WHEN** multiple schemas are available and the user proposes a change
- **THEN** the ProposeChangeDialog SHALL display a schema combo box and pass `--schema "<schema>"` to the `openspec new change` command

### Requirement: Workflow action panel

The plugin SHALL display the selected change's pipeline status with interactive artifact chips that serve as the primary action surface. Pipeline chips SHALL be clickable (READY → generate, DONE → open file) with right-click context menus. A compact icon action bar SHALL provide secondary actions (Fast-Forward, Verify, Archive, overflow menu). A single-line status strip SHALL show compliance, task progress, and delivery mode. The panel SHALL NOT use a horizontal button row for workflow actions. The content area SHALL use CardLayout to manage NO_CHANGES, FF_INPUT, and PIPELINE views. The Fast-Forward link in the "no changes" card SHALL only be visible when Direct API is configured; when Direct API is not configured, the card SHALL show only the Propose link. The Explore tab context SHALL reflect the current change selection.

#### Scenario: Pipeline visualization
- **WHEN** a change is selected
- **THEN** the panel SHALL show interactive artifact status chips (DONE, READY, BLOCKED, GENERATING) with content-aware scaffolding detection, hover affordances, and click/right-click actions

#### Scenario: Generate via chip click
- **WHEN** the user clicks a READY chip
- **THEN** it SHALL deliver via the selected method (clipboard, editor tab, or Direct API) with a guidance popover showing post-delivery feedback

#### Scenario: Sync Specs in overflow menu
- **WHEN** all artifacts are complete and the change contains delta spec sections
- **THEN** the overflow menu SHALL include a "Sync Specs" item

#### Scenario: Sync Specs hidden from overflow
- **WHEN** the change has no delta spec sections
- **THEN** the overflow menu SHALL NOT include "Sync Specs"

#### Scenario: Compliance chip displayed
- **WHEN** the workflow action panel renders for a selected change
- **THEN** the status strip SHALL include compliance status alongside task progress and delivery mode

#### Scenario: FF link visible with Direct API
- **WHEN** the "no changes" card renders and Direct API is configured
- **THEN** the card SHALL show "Propose or Fast-Forward" with both links active

#### Scenario: FF link hidden without Direct API
- **WHEN** the "no changes" card renders and Direct API is not configured
- **THEN** the card SHALL show only the Propose link without the FF option

#### Scenario: FF input activation
- **WHEN** the user clicks the FF toolbar button or "Fast-Forward" hyperlink
- **THEN** the panel SHALL switch to the FF_INPUT card while keeping the tool selector visible

#### Scenario: Explore tab reflects change context
- **WHEN** a change is selected in the workflow panel
- **THEN** the Explore tab SHALL include that change's details in the assembled context

### Requirement: FF action requires Direct API

The FF menu action SHALL be disabled when Direct API is not configured. When disabled, the action SHALL display a tooltip indicating that an AI provider must be configured in Settings → Tools → OpenSpec. The action SHALL re-evaluate its enabled state on each menu presentation update.

#### Scenario: FF action disabled without Direct API
- **WHEN** the FF menu action updates and Direct API is not configured
- **THEN** the action SHALL be disabled with description "Requires AI provider. Configure in Settings → Tools → OpenSpec."

#### Scenario: FF action enabled with Direct API
- **WHEN** the FF menu action updates and Direct API is configured
- **THEN** the action SHALL be enabled (subject to existing profile-based visibility)

#### Scenario: FF input guard without Direct API
- **WHEN** `activateFfInput()` is called and Direct API is not configured
- **THEN** the panel SHALL show an inline message directing the user to configure an API provider instead of showing the FF form

### Requirement: Generate All

The plugin SHALL sequentially generate all remaining artifacts in dependency order via Direct API with progress reporting and cancellation support.

#### Scenario: Orchestration
- **WHEN** Generate All is triggered
- **THEN** artifacts SHALL generate in DAG dependency order with real-time progress feedback

### Requirement: Apply action

The plugin SHALL assemble a full-context implementation prompt (proposal, specs, design, tasks) and deliver via the selected tool.

#### Scenario: Prompt assembly
- **WHEN** Apply is triggered
- **THEN** the plugin SHALL assemble context from all change artifacts and deliver via the user's preferred method

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

### Requirement: Change selector

The plugin SHALL provide a change selector for switching between active changes with session persistence.

#### Scenario: Multiple changes
- **WHEN** multiple active changes exist
- **THEN** the user SHALL be able to switch between them via dropdown

### Requirement: Tree-to-panel synchronization

The plugin SHALL synchronize the workflow panel's active change with the tree view selection. When the user selects a change node (or a child of a change node) in the tree, the workflow panel SHALL update to display that change's pipeline, icon bar, and status strip. The synchronization SHALL be one-way: tree drives panel.

#### Scenario: Select change in tree
- **WHEN** the user clicks a change node in the tree
- **THEN** the workflow panel SHALL update its active change to match and refresh the pipeline display

#### Scenario: Select child of change node
- **WHEN** the user clicks an artifact or spec node under a change
- **THEN** the workflow panel SHALL update its active change to the parent change

#### Scenario: Select non-change node
- **WHEN** the user clicks a node that is not a change or child of a change (e.g., main specs, config, archive)
- **THEN** the workflow panel SHALL NOT change its active change selection

#### Scenario: Dropdown remains functional
- **WHEN** the user selects a change from the workflow panel's dropdown
- **THEN** the panel SHALL update normally without affecting the tree selection

### Requirement: Background thread for change selection refresh

The plugin SHALL perform artifact status lookups on a background thread when the active change is set via external sources (e.g., tree selection). The EDT SHALL NOT be blocked by CLI or orchestration service calls during change selection.

#### Scenario: setActiveChange dispatches to background thread
- **WHEN** `setActiveChange()` is called from the EDT (e.g., tree selection handler)
- **THEN** the artifact status lookup SHALL execute on a pooled background thread, not on the EDT

#### Scenario: Pipeline updates asynchronously after selection
- **WHEN** `setActiveChange()` dispatches the refresh to a background thread
- **THEN** the pipeline display SHALL update on the EDT via `invokeLater` after the background work completes

### Requirement: FF action registration

The plugin SHALL register a Fast-Forward action in the OpenSpec menu and toolbar, accessible via menu item and keyboard shortcut.

#### Scenario: Menu registration
- **WHEN** the user opens the OpenSpec menu
- **THEN** Fast-Forward SHALL appear as a menu item with appropriate icon

### Requirement: Bulk Archive action registration

The plugin SHALL register a Bulk Archive action in the OpenSpec menu for archiving multiple changes at once.

#### Scenario: Menu registration
- **WHEN** the user opens the OpenSpec menu
- **THEN** Bulk Archive SHALL appear as a menu item

