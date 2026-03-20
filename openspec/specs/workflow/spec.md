# Workflow

## Purpose
Artifact generation pipeline: propose, generate, apply, archive actions with delivery routing and progress feedback.
## Requirements
### Requirement: Propose action

The plugin SHALL create a new change with all required artifacts (proposal.md, design.md, tasks.md, specs/) via built-in scaffolding matching the OpenSpec 1.2.0 template structure.

#### Scenario: Change creation
- **WHEN** the user proposes a change
- **THEN** the plugin SHALL create the change directory with all artifacts and refresh the tool window synchronously

### Requirement: Workflow action panel

The plugin SHALL display the selected change's pipeline status with interactive artifact chips that serve as the primary action surface. Pipeline chips SHALL be clickable (READY → generate, DONE → open file) with right-click context menus. A compact icon action bar SHALL provide secondary actions (Fast-Forward, Verify, Archive, overflow menu). A single-line status strip SHALL show compliance, task progress, and delivery mode. The panel SHALL NOT use a horizontal button row for workflow actions.

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

The plugin SHALL move completed changes to `openspec/changes/archive/YYYY-MM-DD-<name>/` with post-archive commit, push, and tracker updates. A Bulk Archive option SHALL be available when multiple active changes exist.

#### Scenario: Archive flow
- **WHEN** the user archives a change
- **THEN** the change SHALL be moved to the archive directory and post-archive steps SHALL execute

#### Scenario: Bulk archive entry point
- **WHEN** multiple active changes exist
- **THEN** a Bulk Archive action SHALL be available in the menu and toolbar

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

