## ADDED Requirements

### Requirement: Icon action bar buttons

The plugin SHALL display a compact row of icon buttons below the pipeline chips for change-specific workflow actions only: Revise, Continue (only when the profile includes `continue`), Apply, Verify, Sync Specs and Archive. The icon bar SHALL NOT include creation actions (Fast-Forward, Start New Change). A muted label at its left edge SHALL show the active change name, anchoring the icons to that change.

#### Scenario: Icon bar contents
- **WHEN** the pipeline card is displayed for an active change
- **THEN** the icon bar SHALL show the active change name as a muted label, followed by Revise, Continue (when the profile includes `continue`), Apply, Verify, Sync Specs and Archive icons

#### Scenario: Icon bar excludes creation actions
- **WHEN** the icon bar is rendered
- **THEN** Fast-Forward and Start New Change SHALL NOT appear in the icon bar

#### Scenario: Change name badge
- **WHEN** the icon bar is displayed
- **THEN** a muted label showing the active change name SHALL appear at the left edge of the icon bar

#### Scenario: Apply button enabled state
- **WHEN** the artifacts Apply needs are complete
- **THEN** the Apply icon SHALL be enabled with tooltip "Apply: <change-name>"

#### Scenario: Apply button disabled state
- **WHEN** an artifact Apply needs is not complete
- **THEN** the Apply icon SHALL be disabled with tooltip "Apply (finish the artifacts it needs first)"

#### Scenario: Verify button enabled state
- **WHEN** all artifacts are complete
- **THEN** the Verify icon SHALL be enabled with tooltip "Verify: <change-name>"

#### Scenario: Verify button disabled state
- **WHEN** artifacts are not all complete
- **THEN** the Verify icon SHALL be disabled with tooltip "Verify (complete all artifacts first)"

#### Scenario: Continue button follows the profile
- **WHEN** the profile does not include `continue`
- **THEN** the icon bar SHALL NOT show a Continue icon

#### Scenario: Continue button state
- **WHEN** the profile includes `continue`
- **THEN** the Continue icon SHALL be enabled with tooltip "Continue: <change-name>" while the change has a ready artifact, and disabled with tooltip "Continue (all artifacts done)" once every artifact is done

## REMOVED Requirements

### Requirement: Compact icon action bar

**Reason**: It listed a Compliance button and an overflow menu the icon bar no longer has, and omitted Revise. Its scenario titles name the Compliance button, so it can't be corrected in place.

**Migration**: Replaced by "Icon action bar buttons", which lists the panel's real buttons (Revise, Continue when the profile includes it, Apply, Verify, Sync Specs, Archive) with the same placement, change-name badge and creation-action exclusion.
