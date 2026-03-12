## MODIFIED Requirements

### Requirement: Tree View Display

The plugin SHALL provide a tool window that displays OpenSpec project contents in a navigable tree structure, with a Workflow Action Panel below the tree for guided artifact generation. The toolbar SHALL contain only project-level operations — change-scoped actions are handled exclusively by the Workflow Action Panel and main menu. When the project is not fully set up, the tool window SHALL display a state-aware getting-started panel instead of the tree.

#### Scenario: Tree structure
- **WHEN** the tool window is opened and the project has active changes
- **THEN** it SHALL display a tree with top-level nodes: Specs, Changes, Archive
- **AND** a Workflow Action Panel SHALL be visible below the tree showing the selected change and generation controls

#### Scenario: Tool window layout
- **WHEN** the tool window is visible and the project is fully set up with active changes
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

#### Scenario: Getting-started panel displayed when not fully set up
- **WHEN** the tool window is opened and the project does not have active changes or is not initialized
- **THEN** the tool window SHALL display the `GettingStartedPanel` instead of the tree and workflow panel

## ADDED Requirements

### Requirement: Tree expansion state preservation
The tree view SHALL preserve its expanded/collapsed node state across refreshes and updates.

#### Scenario: Refresh preserves expansion state
- **WHEN** the user has expanded specific tree nodes (e.g., Specs, a change folder)
- **AND** a tree refresh occurs (manual or automatic via file watcher)
- **THEN** the previously expanded nodes SHALL remain expanded and collapsed nodes SHALL remain collapsed

#### Scenario: Update preserves expansion state
- **WHEN** the tree model is updated (e.g., new artifact detected, status change)
- **THEN** the expansion state of all existing nodes SHALL be preserved
