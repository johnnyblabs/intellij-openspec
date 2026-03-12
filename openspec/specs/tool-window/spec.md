# Tool Window

## Purpose
Tree-based UI panel for browsing specs, changes, and archives.

## Requirements

### Requirement: Tree View Display

The plugin SHALL provide a tool window that displays OpenSpec project contents in a navigable tree structure, with a Workflow Action Panel below the tree for guided artifact generation. The toolbar SHALL contain only project-level operations — change-scoped actions are handled exclusively by the Workflow Action Panel and main menu.

#### Scenario: Tree structure
- **WHEN** the tool window is opened
- **THEN** it SHALL display a tree with top-level nodes: Specs, Changes, Archive
- **AND** a Workflow Action Panel SHALL be visible below the tree showing the selected change and generation controls

#### Scenario: Tool window layout
- **WHEN** the tool window is visible
- **THEN** the layout from top to bottom SHALL be: toolbar (project-level actions only), tree, workflow action panel, status bar

#### Scenario: Tool window toolbar contents
- **WHEN** the tool window toolbar is rendered
- **THEN** it SHALL contain exactly four buttons: Refresh, Validate, Propose, Setup Wizard
- **AND** it SHALL NOT contain Apply, Archive, Generate Artifact, or Generate All actions

#### Scenario: Toolbar icons use standard IntelliJ icons
- **WHEN** the toolbar buttons are rendered
- **THEN** Refresh SHALL use `AllIcons.Actions.Refresh`
- **AND** Propose SHALL use `AllIcons.General.Add`
- **AND** Validate SHALL use the custom `requirement.svg` icon
- **AND** Setup Wizard SHALL use `AllIcons.General.GearPlain`

### Requirement: File Navigation

The plugin SHALL allow users to navigate to spec files by double-clicking tree nodes.

#### Scenario: Open spec file
- GIVEN a spec tree node is visible
- WHEN the user double-clicks the node
- THEN the corresponding file SHALL open in the editor

### Requirement: Auto Refresh

The tool window SHALL automatically refresh when files in the `openspec/` directory change.

#### Scenario: File system change
- GIVEN the tool window is visible
- WHEN a file is added or modified under `openspec/`
- THEN the tree SHALL refresh to reflect the change
- AND the tree SHALL preserve its expansion state across the refresh

### Requirement: Getting Started Panel

The tool window SHALL display a state-aware Getting Started panel instead of the normal tree view when the project is not fully set up.

#### Scenario: Project not initialized
- **WHEN** the tool window opens and no `openspec/` directory exists
- **THEN** the panel SHALL display an "Initialize your project" card with an Initialize button
- **AND** a "Run Setup Wizard" hyperlink

#### Scenario: AI not configured
- **WHEN** the project is initialized but no delivery method is configured
- **THEN** the panel SHALL display a "Configure your AI tool" card with a Configure button
- **AND** a "Run Setup Wizard" hyperlink

#### Scenario: No active changes
- **WHEN** the project is initialized, AI is configured, but no active changes exist
- **THEN** the panel SHALL display a "Create your first change" card with a Propose button

#### Scenario: Ready state
- **WHEN** the project has active changes
- **THEN** the normal tree view and workflow panel SHALL be displayed

### Requirement: Setup Wizard Auto-Launch

The setup wizard SHALL auto-launch on first tool window open when setup has not been completed.

#### Scenario: First open
- **WHEN** the tool window opens and `setupCompleted` is false
- **THEN** the Setup Wizard dialog SHALL open automatically
- **AND** the tool window SHALL rebuild after the wizard closes

### Requirement: Actionable Empty State Nodes

Tree hint nodes for empty sections SHALL be actionable via double-click.

#### Scenario: No active changes hint
- **WHEN** the user double-clicks the "No active changes" hint node
- **THEN** the Propose action SHALL be triggered

#### Scenario: Not initialized hint
- **WHEN** the user double-clicks the "No openspec/ directory found" hint node
- **THEN** the Initialize action SHALL be triggered
