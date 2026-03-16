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

The plugin SHALL display the selected change's pipeline status with a compact artifact pipeline, tool/delivery selector, action buttons, and a Sync Specs button that appears when all artifacts are complete and delta specs exist.

#### Scenario: Pipeline visualization
- **WHEN** a change is selected
- **THEN** the panel SHALL show artifact status chips (DONE, READY, BLOCKED) with content-aware scaffolding detection

#### Scenario: Generate button
- **WHEN** the user clicks Generate
- **THEN** it SHALL deliver via the selected method (clipboard, editor tab, or Direct API) with tool-specific post-delivery guidance

#### Scenario: Sync Specs button visibility
- **WHEN** all artifacts are complete and the change contains delta spec sections
- **THEN** the panel SHALL show a Sync Specs button alongside the Verify and Archive buttons

#### Scenario: Sync Specs button hidden
- **WHEN** the change has no delta spec sections
- **THEN** the Sync Specs button SHALL NOT be visible

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

The plugin SHALL move completed changes to `openspec/changes/archive/YYYY-MM-DD-<name>/` with post-archive commit, push, and tracker updates.

#### Scenario: Archive flow
- **WHEN** the user archives a change
- **THEN** the change SHALL be moved to the archive directory and post-archive steps SHALL execute

### Requirement: Change selector

The plugin SHALL provide a change selector for switching between active changes with session persistence.

#### Scenario: Multiple changes
- **WHEN** multiple active changes exist
- **THEN** the user SHALL be able to switch between them via dropdown
