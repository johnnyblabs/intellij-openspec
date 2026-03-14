# Onboarding

## Purpose
First-run experience: setup wizard, getting started panel, guided first proposal, and branded visuals.

## Requirements

### Requirement: Setup wizard

The plugin SHALL provide a multi-step wizard (CLI detection, AI tool selection, delivery method, project init) that auto-launches on first tool window open.

#### Scenario: Wizard steps
- **WHEN** the wizard opens
- **THEN** it SHALL guide users through CLI detection, AI tool configuration, project initialization, and summary

#### Scenario: Create Your First Change
- **WHEN** the user clicks "Create Your First Change" on the wizard's done step
- **THEN** it SHALL invoke the OpenSpec.Propose action (not just open the dialog)

### Requirement: Getting started panel

The plugin SHALL display a state-aware onboarding panel with states: NOT_INITIALIZED, NO_AI_CONFIGURED, NO_CHANGES, and READY.

#### Scenario: State detection priority
- **WHEN** detecting state, the plugin SHALL check in order: initialized → has active/archived changes → AI configured → no changes

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
