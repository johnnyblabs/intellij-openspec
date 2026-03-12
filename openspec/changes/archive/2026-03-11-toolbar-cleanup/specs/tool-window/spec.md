## MODIFIED Requirements

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
- **THEN** it SHALL contain exactly three buttons: Refresh, Validate, Propose
- **AND** it SHALL NOT contain Apply, Archive, Generate Artifact, or Generate All actions

#### Scenario: Toolbar icons use standard IntelliJ icons
- **WHEN** the toolbar buttons are rendered
- **THEN** Refresh SHALL use `AllIcons.Actions.Refresh`
- **AND** Propose SHALL use `AllIcons.General.Add`
- **AND** Validate SHALL use the custom `requirement.svg` icon
