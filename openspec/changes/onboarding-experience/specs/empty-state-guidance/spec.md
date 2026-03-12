## ADDED Requirements

### Requirement: Empty state factory
The plugin SHALL provide an `EmptyStateFactory` utility that creates consistent empty-state panels with an icon, title, description, and optional action button.

#### Scenario: Factory produces consistent panels
- **WHEN** an empty state is created via `EmptyStateFactory`
- **THEN** the panel SHALL display a centered layout with an icon (16x16), bold title, description text in secondary color, and an optional action button

### Requirement: Tree empty states with actions
The tree view SHALL display actionable empty states in each section when no content is present, replacing the current passive gray HINT text.

#### Scenario: Specs section empty
- **WHEN** the Specs tree section has no children
- **THEN** the tree SHALL display "No specs yet" with description "Specs are created when you propose a change"

#### Scenario: Changes section empty
- **WHEN** the Changes tree section has no children
- **THEN** the tree SHALL display "No active changes" with a "Propose" action button that opens the Propose Change dialog

#### Scenario: Root when not initialized
- **WHEN** the project has no `openspec/` directory
- **THEN** the tree SHALL display "Not an OpenSpec project" with an "Initialize" action button that calls `ScaffoldingService.initOpenSpec()`

### Requirement: Workflow panel empty state
The Workflow Action Panel SHALL display an actionable empty state when no change is selected.

#### Scenario: No change selected
- **WHEN** the Workflow Action Panel is visible and no change is selected in the change selector
- **THEN** the panel SHALL display "Select a change to see its workflow" with a prompt directing the user to the change selector or a "Propose" button if no changes exist

### Requirement: Console empty state
The Console panel SHALL display guidance text when empty.

#### Scenario: Console has no output
- **WHEN** the Console panel is opened and no CLI commands have been run
- **THEN** the panel SHALL display "CLI output will appear here when you run OpenSpec commands"

### Requirement: State-aware getting started panel
The tool window SHALL display a `GettingStartedPanel` that adapts its content based on project and configuration state, replacing the current static welcome panel.

#### Scenario: Not initialized state
- **WHEN** the tool window opens and `openspec/` does not exist
- **THEN** the getting-started panel SHALL show an "Initialize your project" card with an Initialize button and a "Run Setup Wizard" link

#### Scenario: Initialized but no AI configured
- **WHEN** the tool window opens and `openspec/` exists but `preferredDeliveryMethod` is empty
- **THEN** the getting-started panel SHALL show a "Configure your AI tool" card with a configuration button and a "Run Setup Wizard" link

#### Scenario: Initialized and configured but no changes
- **WHEN** the tool window opens and `openspec/` exists and AI is configured but no active changes exist
- **THEN** the getting-started panel SHALL show a "Create your first change" card with a Propose button

#### Scenario: Has active changes
- **WHEN** the tool window opens and active changes exist
- **THEN** the getting-started panel SHALL NOT be shown and the standard tool window (tree + workflow panel) SHALL be displayed
