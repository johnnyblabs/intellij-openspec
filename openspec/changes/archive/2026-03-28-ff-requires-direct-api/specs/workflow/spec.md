## MODIFIED Requirements

### Requirement: Workflow action panel

The plugin SHALL display the selected change's pipeline status with interactive artifact chips that serve as the primary action surface. Pipeline chips SHALL be clickable (READY → generate, DONE → open file) with right-click context menus. A compact icon action bar SHALL provide secondary actions (Fast-Forward, Verify, Archive, overflow menu). A single-line status strip SHALL show compliance, task progress, and delivery mode. The panel SHALL NOT use a horizontal button row for workflow actions. The Fast-Forward link in the "no changes" card SHALL only be visible when Direct API is configured; when Direct API is not configured, the card SHALL show only the Propose link.

#### Scenario: Pipeline visualization
- **WHEN** a change is selected
- **THEN** the panel SHALL show interactive artifact status chips (DONE, READY, BLOCKED, GENERATING) with content-aware scaffolding detection, hover affordances, and click/right-click actions

#### Scenario: Generate via chip click
- **WHEN** the user clicks a READY chip
- **THEN** it SHALL deliver via the selected method (clipboard, editor tab, or Direct API) with a guidance popover showing post-delivery feedback

#### Scenario: Sync Specs in overflow menu
- **WHEN** all artifacts are complete and the change contains delta spec sections
- **THEN** the overflow menu SHALL include a "Sync Specs" item

#### Scenario: Sync Specs hidden from overflow
- **WHEN** the change has no delta spec sections
- **THEN** the overflow menu SHALL NOT include "Sync Specs"

#### Scenario: Compliance chip displayed
- **WHEN** the workflow action panel renders for a selected change
- **THEN** the status strip SHALL include compliance status alongside task progress and delivery mode

#### Scenario: FF link visible with Direct API
- **WHEN** the "no changes" card renders and Direct API is configured
- **THEN** the card SHALL show "Propose or Fast-Forward" with both links active

#### Scenario: FF link hidden without Direct API
- **WHEN** the "no changes" card renders and Direct API is not configured
- **THEN** the card SHALL show only the Propose link without the FF option

## ADDED Requirements

### Requirement: FF action requires Direct API

The FF menu action SHALL be disabled when Direct API is not configured. When disabled, the action SHALL display a tooltip indicating that an AI provider must be configured in Settings → Tools → OpenSpec. The action SHALL re-evaluate its enabled state on each menu presentation update.

#### Scenario: FF action disabled without Direct API
- **WHEN** the FF menu action updates and Direct API is not configured
- **THEN** the action SHALL be disabled with description "Requires AI provider. Configure in Settings → Tools → OpenSpec."

#### Scenario: FF action enabled with Direct API
- **WHEN** the FF menu action updates and Direct API is configured
- **THEN** the action SHALL be enabled (subject to existing profile-based visibility)

#### Scenario: FF input guard without Direct API
- **WHEN** `activateFfInput()` is called and Direct API is not configured
- **THEN** the panel SHALL show an inline message directing the user to configure an API provider instead of showing the FF form