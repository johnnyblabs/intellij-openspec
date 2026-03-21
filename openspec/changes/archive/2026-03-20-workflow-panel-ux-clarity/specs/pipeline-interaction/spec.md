## MODIFIED Requirements

### Requirement: Compact icon action bar

The plugin SHALL display a compact row of small icon buttons below the pipeline chips for change-specific workflow actions only. The icon bar SHALL show icons for Verify, Archive, and an overflow menu (⋯). The icon bar SHALL NOT include Fast-Forward (which is a creation action, not a change action). The icon bar SHALL display a muted label at its left edge showing the active change name, anchoring all icons to that change.

#### Scenario: Icon bar contents
- **WHEN** the pipeline card is displayed for an active change
- **THEN** the icon bar SHALL show the active change name as a muted label, followed by Verify, Archive, and overflow menu icons

#### Scenario: Icon bar excludes creation actions
- **WHEN** the icon bar is rendered
- **THEN** Fast-Forward SHALL NOT appear in the icon bar

#### Scenario: Change name badge
- **WHEN** the icon bar is displayed
- **THEN** a muted label showing the active change name SHALL appear at the left edge of the icon bar

## ADDED Requirements

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

### Requirement: Overflow menu groups by scope

The overflow menu SHALL group items by scope with visual separators. Change-scoped items (Apply Tasks, Sync Specs, Compliance Check) SHALL appear first. All-changes items (Archive All Changes...) SHALL appear in a separate group. Creation items (Start New Change..., Fast-Forward...) SHALL appear in a third group.

#### Scenario: Overflow menu structure
- **WHEN** the user clicks the overflow menu icon
- **THEN** the menu SHALL show three groups separated by dividers: change-scoped actions, all-changes actions, and creation actions

#### Scenario: Archive All Changes label
- **WHEN** the overflow menu is displayed
- **THEN** the all-changes archive item SHALL be labeled "Archive All Changes..." (not "Bulk Archive")

#### Scenario: Fast-Forward in overflow menu
- **WHEN** the overflow menu is displayed
- **THEN** "Fast-Forward..." SHALL appear in the creation group at the bottom of the menu