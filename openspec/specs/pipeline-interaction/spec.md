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

