## MODIFIED Requirements

### Requirement: Setup wizard

The plugin SHALL provide a multi-step wizard (CLI detection, AI tool selection, delivery method, project init) that auto-launches on first tool window open only when the project is not initialized. The wizard SHALL NOT launch for any initialized project (READY, NO_CHANGES, or NO_AI_CONFIGURED states). When skipping the wizard for an initialized project, the plugin SHALL auto-set the setup-completed flag to true.

#### Scenario: Wizard steps
- **WHEN** the wizard opens
- **THEN** it SHALL guide users through CLI detection, AI tool configuration, project initialization, and summary

#### Scenario: Create Your First Change
- **WHEN** the user clicks "Create Your First Change" on the wizard's done step
- **THEN** it SHALL invoke the OpenSpec.Propose action (not just open the dialog)

#### Scenario: Wizard launches for uninitialized project
- **WHEN** the tool window opens on a project with no `openspec/` directory and setup has not been completed
- **THEN** the plugin SHALL launch the setup wizard

#### Scenario: Wizard skipped for initialized project
- **WHEN** the tool window opens on a project where `openspec/` exists (regardless of changes or AI configuration)
- **THEN** the plugin SHALL NOT launch the setup wizard and SHALL set setupCompleted to true

### Requirement: Getting started panel

The plugin SHALL display a state-aware onboarding panel only when the project is not initialized. For all initialized states (READY, NO_CHANGES, NO_AI_CONFIGURED), the plugin SHALL display the tree view directly, allowing users to browse specs.

#### Scenario: State detection priority
- **WHEN** detecting state
- **THEN** the plugin SHALL check in order: initialized → has active/archived changes → AI configured → no changes

#### Scenario: Not initialized shows Getting Started
- **WHEN** the project has no `openspec/` directory
- **THEN** the plugin SHALL show the Getting Started panel

#### Scenario: Initialized without changes shows tree
- **WHEN** the project has `openspec/` but no active or archived changes
- **THEN** the plugin SHALL show the tree view (not the Getting Started panel)

#### Scenario: Initialized without AI shows tree
- **WHEN** the project has `openspec/` but no AI delivery method configured
- **THEN** the plugin SHALL show the tree view (not the Getting Started panel)

#### Scenario: Projects with history skip onboarding
- **WHEN** `openspec/changes/archive/` contains archived changes
- **THEN** the panel SHALL return READY and show the tree view

#### Scenario: External file changes
- **WHEN** files under `openspec/` change externally
- **THEN** the panel SHALL detect the change via file watcher and auto-transition state
