# Onboarding

## Purpose
First-run experience: setup wizard, getting started panel, guided first proposal, and branded visuals.

## Requirements

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

### Requirement: Guided first proposal

The plugin SHALL provide contextual guidance for first-time proposal creation including placeholder text, workflow banner, and "What's Next" notification.

#### Scenario: First proposal
- **WHEN** the user creates their first change
- **THEN** a workflow banner SHALL appear in the dialog and a "What's Next" notification SHALL fire after creation

### Requirement: Branded visuals

The plugin SHALL display the 32x32 OpenSpec brand icon and "Spec-Driven Development" tagline in the getting started panel and setup wizard (welcome + done steps).

#### Scenario: Brand icon
- **WHEN** onboarding screens are displayed
- **THEN** the brand icon SHALL render with automatic dark theme variant support

### Requirement: Install hints name every upstream install route

Every install hint the plugin shows SHALL come from one source. It SHALL lead with upstream's standalone installer for the host OS, then name npm and, outside Windows, the Homebrew formula, using upstream's README commands, and SHALL link to upstream's install instructions. The plugin SHALL only show the commands, never run an installer, change PATH, or remove an install.

#### Scenario: Hint on macOS or Linux
- **WHEN** the plugin shows an install hint on macOS or Linux
- **THEN** it SHALL name the standalone installer command `curl -fsSL https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.sh | sh` first
- **AND** it SHALL name `npm install -g @fission-ai/openspec@latest` and `brew install openspec` as alternatives

#### Scenario: Hint on Windows
- **WHEN** the plugin shows an install hint on Windows
- **THEN** it SHALL name the standalone installer command `irm https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.ps1 | iex` first
- **AND** it SHALL name `npm install -g @fission-ai/openspec@latest` as the alternative, and SHALL NOT suggest Homebrew or the `curl … | sh` command

#### Scenario: Notifications link to the install instructions
- **WHEN** the CLI-not-found notification, or the warning for a CLI command run without the CLI, is shown
- **THEN** it SHALL say that the CLI can be installed without Node.js, or with npm or Homebrew, and SHALL offer an action that opens upstream's install instructions, alongside its existing Settings action

#### Scenario: Dialogs show the commands and the link
- **WHEN** the setup wizard's CLI step or the Manage AI Tools footer shows the install hint
- **THEN** it SHALL show the host OS's standalone command and its alternatives, and a link that opens upstream's install instructions

#### Scenario: The Settings tooltip lists the standalone location
- **WHEN** the user hovers over the Settings CLI-path field
- **THEN** the tooltip SHALL list the standalone install location for the host OS (`~/.local/bin/openspec`, or `%LOCALAPPDATA%\Programs\openspec\openspec.exe` on Windows) next to the existing package-manager examples

#### Scenario: One source for the wording
- **WHEN** the CLI-not-found notification, the warning for a CLI command run without the CLI, the setup wizard's CLI step, the Manage AI Tools footer or the Settings CLI-path tooltip shows how to install the CLI
- **THEN** its wording SHALL come from the shared install hint, and no other plugin code SHALL write out its own npm-only install command

#### Scenario: The below-floor upgrade hint is unchanged
- **WHEN** the detected CLI is older than 1.3.0
- **THEN** the upgrade notification SHALL keep its existing npm upgrade command, because no standalone build is older than 1.15
