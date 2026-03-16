## MODIFIED Requirements

### Requirement: Getting started panel

The plugin SHALL display a state-aware onboarding panel with states: NOT_INITIALIZED, NO_AI_CONFIGURED, NO_CHANGES, and READY. The READY state SHALL highlight new v0.3.0 workflows (FF, Continue, Verify) in the getting started guidance.

#### Scenario: State detection priority
- **WHEN** detecting state, the plugin SHALL check in order: initialized → has active/archived changes → AI configured → no changes

#### Scenario: Projects with history skip onboarding
- **WHEN** `openspec/changes/archive/` contains archived changes
- **THEN** the panel SHALL return READY and show the tree view

#### Scenario: External file changes
- **WHEN** files under `openspec/` change externally
- **THEN** the panel SHALL detect the change via file watcher and auto-transition state

#### Scenario: New workflow guidance
- **WHEN** the getting started panel shows the NO_CHANGES state
- **THEN** it SHALL mention Fast-Forward as an alternative to Propose for quickly creating a fully-specced change
