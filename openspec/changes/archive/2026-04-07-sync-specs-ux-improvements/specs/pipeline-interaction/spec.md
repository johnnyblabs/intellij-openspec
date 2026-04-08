## MODIFIED Requirements

### Requirement: Compact icon action bar

The plugin SHALL display a compact row of small icon buttons below the pipeline chips for change-specific workflow actions only. The icon bar SHALL show icons for Apply, Compliance, Verify, Sync Specs, Archive, and an overflow menu (⋯). The icon bar SHALL NOT include Fast-Forward or Start New Change (which are creation actions, not change actions). The icon bar SHALL display a muted label at its left edge showing the active change name, anchoring all icons to that change.

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

### Requirement: Contextual tooltips on icon bar

The plugin SHALL display tooltips on all icon bar buttons that include the action name, target change name, and disabled-state reason when applicable.

#### Scenario: Enabled icon tooltip
- **WHEN** the user hovers over an enabled Verify icon
- **THEN** the tooltip SHALL display "Verify: <change-name>"

#### Scenario: Disabled Verify tooltip
- **WHEN** the user hovers over a disabled Verify icon
- **THEN** the tooltip SHALL display "Verify (complete all artifacts first)"

#### Scenario: Disabled Archive tooltip
- **WHEN** the user hovers over a disabled Archive icon
- **THEN** the tooltip SHALL display "Archive (complete all artifacts and tasks first)"

#### Scenario: Enabled Archive tooltip
- **WHEN** the user hovers over an enabled Archive icon
- **THEN** the tooltip SHALL display "Archive: <change-name>"

#### Scenario: Enabled Apply tooltip
- **WHEN** the user hovers over an enabled Apply icon
- **THEN** the tooltip SHALL display "Apply: <change-name>"

#### Scenario: Disabled Apply tooltip
- **WHEN** the user hovers over a disabled Apply icon
- **THEN** the tooltip SHALL display "Apply (complete all artifacts first)"

#### Scenario: Enabled Compliance tooltip
- **WHEN** the user hovers over an enabled Compliance icon
- **THEN** the tooltip SHALL display "Compliance: <change-name>"

#### Scenario: Disabled Compliance tooltip
- **WHEN** the user hovers over a disabled Compliance icon
- **THEN** the tooltip SHALL display "Compliance (complete all artifacts first)"

#### Scenario: Enabled Sync Specs tooltip
- **WHEN** the user hovers over an enabled Sync Specs icon and the change has delta specs
- **THEN** the tooltip SHALL display "Sync Specs: <change-name>"

#### Scenario: Disabled Sync Specs tooltip
- **WHEN** the user hovers over a disabled Sync Specs icon and the change has no delta specs
- **THEN** the tooltip SHALL display "Sync Specs (no delta specs)"

### Requirement: Overflow menu groups by scope

The overflow menu SHALL contain only change-scoped actions. Cancel Generation SHALL appear when generation is in progress. Sync Specs SHALL NOT appear in the overflow menu. Creation actions (Start New Change, Fast-Forward) and all-changes actions (Archive All Changes) SHALL NOT appear in the overflow menu.

#### Scenario: Overflow menu structure
- **WHEN** the user clicks the overflow menu icon and generation is in progress
- **THEN** the menu SHALL show Cancel Generation

#### Scenario: Overflow menu when idle
- **WHEN** the user clicks the overflow menu icon and no generation is in progress
- **THEN** the menu SHALL be empty or not shown

#### Scenario: Overflow menu excludes Sync Specs
- **WHEN** the overflow menu is displayed
- **THEN** Sync Specs SHALL NOT appear in the menu

#### Scenario: Overflow menu excludes creation actions
- **WHEN** the overflow menu is displayed
- **THEN** Start New Change and Fast-Forward SHALL NOT appear in the menu

#### Scenario: Overflow menu excludes all-changes actions
- **WHEN** the overflow menu is displayed
- **THEN** Archive All Changes SHALL NOT appear in the menu

## ADDED Requirements

### Requirement: Sync Specs icon button

The plugin SHALL display a Sync Specs icon button in the icon action bar, positioned between Verify and Archive. The button SHALL use the `AllIcons.Actions.Download` icon. The button SHALL be enabled only when the active change contains delta spec sections (`hasDeltaSpecs` is true). The button SHALL trigger the existing `onSyncSpecs()` action when clicked.

#### Scenario: Sync Specs button position
- **WHEN** the icon bar is rendered for an active change
- **THEN** the Sync Specs button SHALL appear after Verify and before Archive

#### Scenario: Sync Specs enabled with delta specs
- **WHEN** the active change contains delta spec sections
- **THEN** the Sync Specs button SHALL be enabled

#### Scenario: Sync Specs disabled without delta specs
- **WHEN** the active change has no delta spec sections
- **THEN** the Sync Specs button SHALL be disabled

#### Scenario: Sync Specs click triggers sync
- **WHEN** the user clicks the enabled Sync Specs button
- **THEN** the plugin SHALL invoke the sync specs workflow (diff preview and confirmation)
