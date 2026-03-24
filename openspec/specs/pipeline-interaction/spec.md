# Pipeline Interaction

## Purpose
Interactive pipeline chip behavior: click actions, visual affordances, context menus, icon action bar, status strip, and layout.
## Requirements
### Requirement: Chip click triggers generation
The plugin SHALL trigger artifact generation when the user clicks a pipeline chip in READY state. The generation SHALL use the currently selected delivery method. Chips in DONE, BLOCKED, or GENERATING states SHALL NOT trigger generation on click.

#### Scenario: Click READY chip
- **WHEN** the user clicks a pipeline chip with READY status
- **THEN** the plugin SHALL trigger generation for that artifact using the selected delivery method

#### Scenario: Click DONE chip opens file
- **WHEN** the user clicks a pipeline chip with DONE status
- **THEN** the plugin SHALL open the artifact file in the editor

#### Scenario: Click BLOCKED chip does nothing
- **WHEN** the user clicks a pipeline chip with BLOCKED status
- **THEN** no action SHALL be triggered

#### Scenario: Click GENERATING chip does nothing
- **WHEN** the user clicks a pipeline chip with GENERATING status
- **THEN** no action SHALL be triggered

### Requirement: Chip visual affordances
The plugin SHALL display visual affordances on pipeline chips that communicate interactivity. READY chips SHALL show a hand cursor and a hover effect. DONE chips SHALL show a hand cursor. BLOCKED chips SHALL show the default cursor. All chips SHALL display a tooltip describing their state and available action.

#### Scenario: READY chip hover effect
- **WHEN** the user hovers over a READY pipeline chip
- **THEN** the chip SHALL display a hand cursor, a subtle scale or highlight effect, and a tooltip "Click to generate"

#### Scenario: DONE chip hover
- **WHEN** the user hovers over a DONE pipeline chip
- **THEN** the chip SHALL display a hand cursor and a tooltip "Click to open · Right-click for options"

#### Scenario: BLOCKED chip tooltip
- **WHEN** the user hovers over a BLOCKED pipeline chip
- **THEN** the chip SHALL display the default cursor and a tooltip listing the dependency names it is waiting on (e.g., "Waiting on: proposal")

### Requirement: Chip right-click context menu
The plugin SHALL display a context menu when the user right-clicks a pipeline chip. The menu items SHALL be appropriate to the chip's current state.

#### Scenario: DONE chip context menu
- **WHEN** the user right-clicks a DONE pipeline chip
- **THEN** the context menu SHALL show "Open file", "Regenerate", and "Copy prompt"

#### Scenario: READY chip context menu
- **WHEN** the user right-clicks a READY pipeline chip
- **THEN** the context menu SHALL show "Generate" and "Copy prompt"

#### Scenario: READY chip context menu with multiple ready
- **WHEN** the user right-clicks a READY pipeline chip and two or more artifacts are in READY state with Direct API configured
- **THEN** the context menu SHALL include "Generate All Remaining" as an additional item

#### Scenario: GENERATING chip context menu
- **WHEN** the user right-clicks a GENERATING pipeline chip
- **THEN** the context menu SHALL show "Cancel"

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

### Requirement: Status strip
The plugin SHALL display a single-line status strip below the icon bar showing compliance status, task progress (if tasks exist), and delivery mode. The status strip SHALL span the full width of the panel. During Generate All, the status strip SHALL show generation progress and elapsed time.

#### Scenario: Steady-state status
- **WHEN** a change is selected and not generating
- **THEN** the status strip SHALL display compliance status, task progress (if tasks.md exists), and current delivery mode in one line

#### Scenario: Generation progress
- **WHEN** Generate All is in progress
- **THEN** the status strip SHALL display "Generating N/M... Xs" with the current delivery mode

### Requirement: Pipeline card layout fills available width
The pipeline card's child panels (pipeline chips, icon bar, status strip) SHALL each expand to fill the full available width of the parent container. The pipeline chips panel SHALL NOT have a horizontal separator border above it. There SHALL be no unnecessary vertical whitespace between the pipeline chips and the icon bar. The icon bar and status strip SHALL use `maxWidth = Integer.MAX_VALUE` sizing so BoxLayout allocates full width.

#### Scenario: Child panels fill width
- **WHEN** the pipeline card is displayed in the tool window
- **THEN** the pipeline chips, icon bar, and status strip SHALL each expand to fill the full available width of the parent container

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

