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

### Requirement: Diff vs current spec from the specs chip

When the specs artifact is done and the change has delta specs, the specs chip's right-click menu SHALL offer "Diff vs current spec" with one entry per capability. Each entry SHALL open the diff of that capability's delta spec against its current main spec. The wording SHALL describe the change compared with the current spec, never what a generation changed.

#### Scenario: One entry per capability
- **WHEN** the user right-clicks the DONE specs chip of a change with delta specs for `auth` and `billing`
- **THEN** the menu SHALL include "Diff vs current spec" with entries for `auth` and `billing`, after "Open file", "Regenerate" and "Copy prompt"

#### Scenario: An entry opens the capability's diff
- **WHEN** the user chooses the `auth` entry
- **THEN** the plugin SHALL open the diff of `specs/auth/spec.md` in the change against the current main spec

#### Scenario: A new capability
- **WHEN** a capability has no main spec yet
- **THEN** its entry SHALL open the diff against an empty "New capability — no existing spec" side, as Preview Diff does

#### Scenario: Not offered without delta specs or on other chips
- **WHEN** the change has no delta specs, or the chip is not the specs artifact
- **THEN** the menu SHALL NOT include "Diff vs current spec"

### Requirement: Regenerate with an optional note

Choosing Regenerate on a DONE chip SHALL open one dialog that asks what should change, with an optional note. When the artifact has completed downstream artifacts, the same dialog SHALL warn that they may become inconsistent. The note SHALL be added to the artifact's prompt for that regeneration only, in every delivery mode, and SHALL NOT be saved anywhere.

#### Scenario: Regenerate with a note
- **WHEN** the user chooses Regenerate on the DONE `design` chip, types "drop the caching section" and confirms
- **THEN** the plugin SHALL regenerate `design` through the selected delivery mode with a prompt that includes the note exactly once, after the dependencies, in a section marked as subordinate to the schema's instructions

#### Scenario: Regenerate without a note
- **WHEN** the user confirms the dialog with the note left blank
- **THEN** the prompt SHALL be byte-identical to the prompt Regenerate builds without a note

#### Scenario: Downstream warning in the same dialog
- **WHEN** the user chooses Regenerate on a chip whose artifact has completed downstream artifacts
- **THEN** the dialog SHALL name those artifacts and warn that they may become inconsistent, and no second confirmation SHALL follow

#### Scenario: Cancel
- **WHEN** the user cancels the dialog
- **THEN** nothing SHALL be generated, copied or written

#### Scenario: The note is not saved
- **WHEN** a regeneration with a note completes or is cancelled
- **THEN** the note SHALL NOT be written to settings, `.openspec.yaml`, `config.yaml` or any file other than the artifact's own output, and the next Regenerate dialog SHALL open with an empty note

#### Scenario: Copy prompt is unchanged
- **WHEN** the user chooses Copy prompt on a chip
- **THEN** no note dialog SHALL open and the copied prompt SHALL be unchanged

### Requirement: Show Local History from a DONE chip

A DONE chip's right-click menu SHALL offer "Show Local History", which opens the IDE's own Local History view for the artifact's file, or for the change's `specs/` folder when the artifact is `specs`. The item SHALL be left out when that file or folder doesn't exist, when Local History doesn't track it, or when the IDE has no Local History view. The wording SHALL say "Local History", never an OpenSpec "version".

#### Scenario: A single-file artifact
- **WHEN** the user right-clicks the DONE `design` chip and chooses "Show Local History"
- **THEN** the IDE's Local History view SHALL open for the change's `design.md`

#### Scenario: The specs artifact
- **WHEN** the user chooses "Show Local History" on the DONE `specs` chip
- **THEN** the IDE's Local History view SHALL open for the change's `specs/` folder

#### Scenario: Nothing to show
- **WHEN** the artifact's file doesn't exist, Local History doesn't track it, or the IDE has no Local History view
- **THEN** the menu SHALL NOT include "Show Local History"
