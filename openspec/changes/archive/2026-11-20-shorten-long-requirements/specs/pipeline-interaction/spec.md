## MODIFIED Requirements

### Requirement: Compact icon action bar

The plugin SHALL display a compact row of icon buttons below the pipeline chips for change-specific workflow actions only: Apply, Compliance, Verify, Sync Specs, Archive, and an overflow menu (⋯). The icon bar SHALL NOT include creation actions (Fast-Forward, Start New Change). A muted label at its left edge SHALL show the active change name, anchoring the icons to that change.

#### Scenario: Icon bar contents
- **WHEN** the pipeline card is displayed for an active change
- **THEN** the icon bar SHALL show the active change name as a muted label, followed by Apply, Compliance, Verify, Sync Specs, Archive, and overflow menu icons

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

