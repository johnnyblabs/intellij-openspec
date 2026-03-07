## MODIFIED Requirements

### Requirement: Tree View Display

The plugin SHALL provide a tool window that displays OpenSpec project contents in a navigable tree structure, with a Workflow Action Panel below the tree for guided artifact generation.

#### Scenario: Tree structure
- **WHEN** the tool window is opened
- **THEN** it SHALL display a tree with top-level nodes: Specs, Changes, Archive
- **THEN** a Workflow Action Panel SHALL be visible below the tree showing the active change status and generation controls

#### Scenario: Tool window layout
- **WHEN** the tool window is visible
- **THEN** the layout from top to bottom SHALL be: toolbar, tree, workflow action panel, status bar
