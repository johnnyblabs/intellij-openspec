## MODIFIED Requirements

### Requirement: Tree View Display

The plugin SHALL provide a tool window that displays OpenSpec project contents in a navigable tree structure, with a Workflow Action Panel below the tree for guided artifact generation. The toolbar SHALL NOT include generate artifact actions — generation is handled exclusively by the Workflow Action Panel.

#### Scenario: Tree structure
- **WHEN** the tool window is opened
- **THEN** it SHALL display a tree with top-level nodes: Specs, Changes, Archive
- **THEN** a Workflow Action Panel SHALL be visible below the tree showing the selected change and generation controls

#### Scenario: Tool window layout
- **WHEN** the tool window is visible
- **THEN** the layout from top to bottom SHALL be: toolbar (without generate actions), tree, workflow action panel, status bar

#### Scenario: Tool window toolbar contents
- **WHEN** the tool window toolbar is rendered
- **THEN** it SHALL contain: Refresh, Validate, Propose, Apply, Archive
- **THEN** it SHALL NOT contain Generate Artifact or Generate All actions
