## ADDED Requirements

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
The plugin SHALL display a compact row of small icon buttons below the pipeline chips for secondary workflow actions. The icons SHALL use IntelliJ's ActionButton style (16x16, gray when unavailable, themed when active). The icon bar SHALL span the full width of the panel with icons right-aligned within it.

#### Scenario: Icon bar contents
- **WHEN** the pipeline card is displayed
- **THEN** the icon bar SHALL show icons for Fast-Forward, Verify, Archive, and an overflow menu (⋯)

#### Scenario: Icon visibility
- **WHEN** artifacts are not all complete
- **THEN** the Verify and Archive icons SHALL be grayed out (disabled)

#### Scenario: Overflow menu contents
- **WHEN** the user clicks the overflow menu icon
- **THEN** the menu SHALL include Sync Specs, Bulk Archive, Apply Tasks, and Compliance Check — with items disabled when not applicable

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
