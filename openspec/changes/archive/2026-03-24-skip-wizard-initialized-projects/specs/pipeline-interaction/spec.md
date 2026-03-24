## MODIFIED Requirements

### Requirement: Compact icon action bar

The plugin SHALL display a compact row of small icon buttons below the pipeline chips for change-specific workflow actions only. The icon bar SHALL show icons for Apply, Compliance, Verify, Archive, and an overflow menu (⋯). The icon bar SHALL NOT include Fast-Forward or Start New Change (which are creation actions, not change actions). The icon bar SHALL display a muted label at its left edge showing the active change name, anchoring all icons to that change.

#### Scenario: Icon bar contents
- **WHEN** the pipeline card is displayed for an active change
- **THEN** the icon bar SHALL show the active change name as a muted label, followed by Apply, Compliance, Verify, Archive, and overflow menu icons

#### Scenario: Icon bar excludes creation actions
- **WHEN** the icon bar is rendered
- **THEN** Fast-Forward and Start New Change SHALL NOT appear in the icon bar

#### Scenario: Change name badge
- **WHEN** the icon bar is displayed
- **THEN** a muted label showing the active change name SHALL appear at the left edge of the icon bar

#### Scenario: Apply button enabled state
- **WHEN** all artifacts are complete
- **THEN** the Apply icon SHALL be enabled with tooltip "Apply: <change-name>"

#### Scenario: Apply button disabled state
- **WHEN** artifacts are not all complete
- **THEN** the Apply icon SHALL be disabled with tooltip "Apply (complete all artifacts first)"

#### Scenario: Compliance button enabled state
- **WHEN** all artifacts are complete
- **THEN** the Compliance icon SHALL be enabled with tooltip "Compliance: <change-name>"

#### Scenario: Compliance button disabled state
- **WHEN** artifacts are not all complete
- **THEN** the Compliance icon SHALL be disabled with tooltip "Compliance (complete all artifacts first)"

### Requirement: Overflow menu groups by scope

The overflow menu SHALL contain only change-scoped actions. Sync Specs SHALL appear as the primary item. Cancel Generation SHALL appear when generation is in progress. Creation actions (Start New Change, Fast-Forward) and all-changes actions (Archive All Changes) SHALL NOT appear in the overflow menu.

#### Scenario: Overflow menu structure
- **WHEN** the user clicks the overflow menu icon
- **THEN** the menu SHALL show only change-scoped actions: Sync Specs and conditionally Cancel Generation

#### Scenario: Overflow menu excludes creation actions
- **WHEN** the overflow menu is displayed
- **THEN** Start New Change and Fast-Forward SHALL NOT appear in the menu

#### Scenario: Overflow menu excludes all-changes actions
- **WHEN** the overflow menu is displayed
- **THEN** Archive All Changes SHALL NOT appear in the menu
