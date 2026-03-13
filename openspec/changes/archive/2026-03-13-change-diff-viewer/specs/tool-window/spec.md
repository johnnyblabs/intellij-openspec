## MODIFIED Requirements

### Requirement: Tree View Display

The plugin SHALL provide a tool window that displays OpenSpec project contents in a navigable tree structure, with a Workflow Action Panel below the tree for guided artifact generation. The toolbar SHALL contain only project-level operations — change-scoped actions are handled exclusively by the Workflow Action Panel and main menu. The tree cell renderer SHALL use `JBColor` for all status colors and distinct icons for each node type. All tree nodes SHALL display contextual tooltips on hover. A search field SHALL appear between the toolbar and the tree for filtering.

#### Scenario: Tree structure
- **WHEN** the tool window is opened
- **THEN** it SHALL display a tree with top-level nodes: Specs, Changes, Archive
- **AND** a Workflow Action Panel SHALL be visible below the tree showing the selected change and generation controls

#### Scenario: Tool window layout
- **WHEN** the tool window is visible
- **THEN** the layout from top to bottom SHALL be: toolbar (project-level actions only), search field, tree, workflow action panel, status bar

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

#### Scenario: Tree node colors adapt to IDE theme
- **WHEN** the tree is rendered in a dark or light theme
- **THEN** all node status colors SHALL use `JBColor` and render correctly in both themes

#### Scenario: Each node type has a distinct icon
- **WHEN** tree nodes are rendered
- **THEN** artifacts SHALL use `artifact.svg`, delta specs SHALL use `delta-spec.svg`, missing artifacts SHALL use `missing-artifact.svg`, and other types SHALL retain their existing icons

#### Scenario: Tree nodes display tooltips on hover
- **WHEN** the user hovers over any tree node
- **THEN** a tooltip SHALL appear with contextual information relevant to that node type

#### Scenario: Delta spec nodes have context menu
- **WHEN** the user right-clicks a DELTA_SPEC node
- **THEN** a context menu SHALL appear with "Preview Diff" and "Open File" actions
