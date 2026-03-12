## MODIFIED Requirements

### Requirement: Tree View Display

The plugin SHALL provide a tool window that displays OpenSpec project contents in a navigable tree structure, with a Workflow Action Panel below the tree for guided artifact generation. The toolbar SHALL contain only project-level operations — change-scoped actions are handled exclusively by the Workflow Action Panel and main menu. The tree cell renderer SHALL use `JBColor` for all status colors and distinct icons for each node type.

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

#### Scenario: Tree node colors adapt to IDE theme
- **WHEN** the tree is rendered in a dark or light theme
- **THEN** all node status colors SHALL use `JBColor` and render correctly in both themes

#### Scenario: Each node type has a distinct icon
- **WHEN** tree nodes are rendered
- **THEN** artifacts SHALL use `artifact.svg`, delta specs SHALL use `delta-spec.svg`, missing artifacts SHALL use `missing-artifact.svg`, and other types SHALL retain their existing icons

### Requirement: Getting Started Panel

The tool window SHALL display a state-aware Getting Started panel instead of the normal tree view when the project is not fully set up. The panel SHALL use the OpenSpec icon for branding.

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

#### Scenario: Branded icon in Getting Started cards
- **WHEN** a Getting Started card is displayed
- **THEN** it SHALL use the OpenSpec icon instead of `AllIcons.General.Information`
