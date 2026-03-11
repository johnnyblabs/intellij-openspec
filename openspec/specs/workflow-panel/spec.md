# Workflow Panel

## Purpose
Guided artifact generation UI with tool selector, one-click Generate button, and interactive pipeline.

## Requirements

### Requirement: Workflow Action Panel Display

The tool window SHALL display a Workflow Action Panel between the tree and the status bar that shows the selected change's name via a change selector, artifact pipeline status, tool/delivery selector, and generation controls.

#### Scenario: Active change with ready artifacts
- **WHEN** an active change is selected with ready artifacts
- **THEN** the panel SHALL display the change selector, a pipeline status row, a tool/delivery selector, and a "Generate [artifact-name]" button for the next ready artifact

#### Scenario: No active change
- **WHEN** no active change exists
- **THEN** the panel SHALL display guidance text such as "No active change" with a hint to use Propose

#### Scenario: All artifacts complete with remaining tasks
- **WHEN** all artifacts for the selected change are done and tasks.md contains incomplete tasks
- **THEN** the panel SHALL display an "Apply Tasks" button with the tool selector, a task progress indicator (e.g., "3/12 tasks complete"), and the pipeline chips showing all-done state

#### Scenario: All artifacts and tasks complete
- **WHEN** all artifacts for the selected change are done and all tasks are marked complete
- **THEN** the panel SHALL display "All complete" in the pipeline with guidance to archive

#### Scenario: Large task list hint
- **WHEN** all artifacts are complete and 10 or more tasks remain incomplete
- **THEN** the panel SHALL display an inline hint suggesting the user review tasks.md before applying

#### Scenario: Generate All button visible and styled
- **WHEN** Direct API is configured and 2 or more artifacts remain to be generated
- **THEN** the panel SHALL display a "Generate All (N)" button with a gradient background, bold font, and an execute icon

#### Scenario: Generate All button hidden without API
- **WHEN** Direct API is not configured
- **THEN** the panel SHALL NOT display the "Generate All" button

#### Scenario: Generate All button hidden with single artifact
- **WHEN** only 1 artifact remains to be generated
- **THEN** the panel SHALL NOT display the "Generate All" button

### Requirement: Inline tool/delivery selector

The WorkflowActionPanel SHALL display a tool/delivery selector dropdown that lets users choose their AI tool or delivery method without leaving the workflow panel.

#### Scenario: Selector shows detected tools
- **WHEN** the workflow panel is displayed with detected AI tools
- **THEN** the tool selector SHALL list each detected tool by name with a type indicator (CLI or IDE)

#### Scenario: Selector shows delivery options
- **WHEN** the workflow panel is displayed
- **THEN** the tool selector SHALL include Direct API (if configured), Editor Tab, and generic Clipboard options below the detected tools

#### Scenario: Tool selection sets delivery mode implicitly
- **WHEN** the user selects a tool from the dropdown
- **THEN** the delivery mode SHALL be set automatically (CLI/IDE tools use clipboard, Direct API uses API)
- **AND** the Generate button label SHALL update to reflect the selection

#### Scenario: Selection is persisted
- **WHEN** the user selects a tool or delivery option
- **THEN** the selection SHALL be saved and restored on next panel load

#### Scenario: No tools and no API configured
- **WHEN** no AI tools are detected and no API key is configured
- **THEN** the panel SHALL display inline help text guiding the user to configure an AI tool or API key

### Requirement: Artifact Pipeline Visualization

The WorkflowActionPanel SHALL display a compact pipeline status row showing the state of each artifact in the change's DAG, using content-aware status that accounts for scaffolding detection. Each chip SHALL be a `JPanel` with tooltip support, click-to-open for completed artifacts, click-to-generate for ready artifacts, and a highlighted border on the current ready artifact.

#### Scenario: Pipeline with mixed states
- **WHEN** the selected change has artifacts in done, ready, and blocked states (after scaffolding override)
- **THEN** the pipeline row SHALL show each artifact as an interactive chip with a visual state indicator (checkmark icon for done, filled circle for ready, empty circle for blocked), a text label, and a tooltip describing the artifact's role

#### Scenario: Pipeline updates after generation
- **WHEN** an artifact generation completes
- **THEN** the pipeline row SHALL refresh to show the updated states

#### Scenario: Scaffolded artifacts show as not done
- **WHEN** an artifact file exists but contains only scaffolding placeholder content
- **THEN** the pipeline chip SHALL show the artifact as ready or blocked (not done)

#### Scenario: Current step is visually prominent
- **WHEN** the pipeline contains a READY artifact
- **THEN** the chip for that artifact SHALL be visually highlighted with a distinct border and background color to indicate it is the current step

#### Scenario: READY chip triggers generation
- **WHEN** the user clicks a READY pipeline chip
- **THEN** the system SHALL trigger generation for that artifact using the selected delivery method

#### Scenario: Generating chip animation
- **WHEN** an artifact is actively being generated during a Generate All operation
- **THEN** the chip SHALL display a pulsing border animation (toggling between bright and dim at 600ms intervals) and use IntelliJ's animated process step icons to indicate active work

#### Scenario: Error chip state
- **WHEN** an artifact fails during Generate All
- **THEN** the chip SHALL display with a red border, red text, and an error icon (AllIcons.General.Error)

### Requirement: Context Menu Generation Routing

When a user triggers generation from the tree context menu on a change node, the panel SHALL select that change and initiate generation.

#### Scenario: Right-click generate on change
- **WHEN** the user right-clicks a change node and selects "Generate..."
- **THEN** the panel SHALL set its active change to that change and trigger the Generate button action

### Requirement: Post-Generation Guidance

After a clipboard or editor-tab delivery, the WorkflowActionPanel SHALL display inline guidance beneath the pipeline with tool-specific instructions, a file-watching status indicator, and a "Copy again" button. The pipeline chips SHALL remain visible so users retain context of where they are.

#### Scenario: Clipboard delivery with CLI tool selected
- **WHEN** an artifact prompt is copied to the clipboard and the selected tool is CLI-based (e.g., Claude Code)
- **THEN** the panel SHALL show inline guidance: a confirmation message, tool-specific instructions, and "Watching for {outputPath}..." status with a "Copy again" button
- **AND** the pipeline chips SHALL remain visible above the guidance text

#### Scenario: Clipboard delivery with IDE panel tool selected
- **WHEN** an artifact prompt is copied to the clipboard and the selected tool is IDE-panel-based (e.g., Copilot)
- **THEN** the panel SHALL show inline guidance with manual save instructions and the expected output path
- **AND** the pipeline chips SHALL remain visible above the guidance text

#### Scenario: Waiting state on Generate button
- **WHEN** an artifact prompt is copied to the clipboard or opened in an editor tab
- **THEN** the Generate button SHALL display "Waiting for [artifact]..." in a disabled state until the file watcher detects the artifact or the user checks for updates

#### Scenario: Auto-refresh on file detection
- **WHEN** the file watcher detects that the expected artifact file has been created or modified
- **THEN** the inline guidance SHALL be dismissed, the DAG cache invalidated, and the panel refreshed to show the next ready artifact

#### Scenario: Copy again
- **WHEN** the user clicks "Copy again" on the inline guidance
- **THEN** the prompt SHALL be re-copied to the clipboard

#### Scenario: Manual check for updates
- **WHEN** the user clicks "Check for updates" on the inline guidance
- **THEN** the DAG cache SHALL be invalidated and the panel refreshed

#### Scenario: Next artifact indicator
- **WHEN** the guidance is displayed after generating an artifact that is not the last in the pipeline
- **THEN** the guidance SHALL show which artifact is next (e.g., "Next: Generate specs")

#### Scenario: Save-path hint in clipboard prompt for CLI tools
- **WHEN** an artifact prompt is copied to the clipboard and the selected tool is CLI-based
- **THEN** the prompt text SHALL include a save-path hint line appended to the end

### Requirement: Auto-Focus After Propose

When a new change is proposed, the workflow panel SHALL automatically select and display that change.

#### Scenario: New change auto-focused
- **WHEN** the user creates a new change via the Propose action
- **THEN** the workflow panel SHALL automatically select the new change and display its pipeline status

### Requirement: Auto-Advance After Generation

The panel SHALL automatically advance to the next ready artifact after a generation completes.

#### Scenario: Direct API generation completes
- **WHEN** an artifact is successfully generated via Direct API
- **THEN** the panel SHALL refresh the DAG status and show the next ready artifact

#### Scenario: Clipboard or editor mode completion
- **WHEN** an artifact prompt is copied to clipboard or opened in editor
- **THEN** the panel SHALL display inline guidance with next-step instructions and a "Check for updates" button that re-checks artifact status when clicked

### Requirement: Generate All UI progress feedback

The WorkflowActionPanel SHALL display real-time progress during a Generate All operation.

#### Scenario: Progress label during generation
- **WHEN** a Generate All operation is in progress
- **THEN** the panel SHALL display a progress bar beneath the pipeline chips showing determinate progress, an elapsed time label, and the generate button text SHALL show the current artifact name and count (e.g., "Generating design... 2/4")

#### Scenario: Pipeline chips update in real-time
- **WHEN** an artifact completes during a Generate All chain
- **THEN** the pipeline chip for that artifact SHALL transition to the done state with a brief green flash animation (300ms), and the next artifact's chip SHALL enter a pulsing generating state

#### Scenario: Buttons disabled during generation
- **WHEN** a Generate All operation is in progress
- **THEN** both the Generate and Generate All buttons SHALL be disabled and a Cancel button SHALL be shown

#### Scenario: Cancel button stops generation
- **WHEN** the user clicks Cancel during a Generate All operation
- **THEN** the system SHALL cancel the remaining chain and restore the panel to its normal state showing current progress

#### Scenario: Completion restores normal state
- **WHEN** a Generate All operation completes successfully
- **THEN** the panel SHALL display a completion celebration (green flash, success message, progress bar turning green) before restoring to normal state with all pipeline chips showing done

#### Scenario: Error shows notification and restores state
- **WHEN** a Generate All operation fails on an artifact
- **THEN** the failed artifact chip SHALL display in red with an error icon, an inline error message SHALL appear, and a "Retry" button SHALL be shown

### Requirement: Post-Apply watching state
The WorkflowActionPanel SHALL display a watching state after an Apply delivery, monitoring tasks.md for progress.

#### Scenario: Watching state after clipboard Apply
- **WHEN** the Apply prompt is copied to the clipboard
- **THEN** the panel SHALL show "Watching for task progress..." with tool-specific instructions and a "Check progress" button
- **AND** the Apply button SHALL display "Waiting..." in a disabled state

#### Scenario: Task progress updates during watch
- **WHEN** tasks.md is modified during the watching state
- **THEN** the task progress indicator SHALL update to reflect the new completion count

#### Scenario: Watch completes when all tasks done
- **WHEN** all tasks are marked complete during the watching state
- **THEN** the watching state SHALL be dismissed and the panel SHALL show "All complete" with archive guidance

### Requirement: Workflow panel shows archive and sync outcomes
The WorkflowActionPanel SHALL surface distinct archive and sync outcomes so users can understand completion state and next actions.

#### Scenario: Archive and sync both succeed
- **WHEN** archive and sync complete successfully for the selected change
- **THEN** the panel SHALL show a success outcome indicating archive and sync are complete

#### Scenario: Archive succeeds but sync fails
- **WHEN** archive completes and sync fails for the selected change
- **THEN** the panel SHALL show archive as complete and sync as failed with recovery guidance

### Requirement: Workflow panel provides sync recovery action
The WorkflowActionPanel SHALL provide a retry path when sync fails after archive.

#### Scenario: Sync retry is available after sync failure
- **WHEN** the panel displays a sync failure for an archived change
- **THEN** the panel SHALL offer a retry action for sync reconciliation

#### Scenario: Sync retry refreshes panel state
- **WHEN** the user triggers sync retry from the panel and retry succeeds
- **THEN** the panel SHALL refresh and show synchronized completion state

### Requirement: Panel section separators
The WorkflowActionPanel SHALL display lightweight visual separators between its logical sections (header, pipeline, controls, guidance) so that each section is visually distinct.

#### Scenario: Separator lines between sections
- **WHEN** the workflow panel is displayed with an active change
- **THEN** the pipeline row, action button row, and guidance panel SHALL each have a 1px top border using the IDE's theme border color (`JBColor.border()`)

#### Scenario: Hidden sections leave no orphaned separators
- **WHEN** a section is hidden (e.g., guidance panel not visible)
- **THEN** the separator associated with that section SHALL also be hidden and SHALL NOT leave empty space

### Requirement: Theme-aware color constants
The WorkflowActionPanel SHALL define all colors as named `JBColor` constants with explicit light and dark theme values, rather than inline `new Color(...)` calls.

#### Scenario: Color constants used for chip states
- **WHEN** pipeline chips are rendered in any state (DONE, READY, GENERATING, ERROR, BLOCKED)
- **THEN** the foreground color, background color, and border color SHALL each reference a named constant

#### Scenario: Color constants used for guidance text
- **WHEN** guidance text is displayed (success message, watching status, error message)
- **THEN** the text foreground color SHALL reference a named constant

#### Scenario: Duplicate color values consolidated
- **WHEN** the same semantic color is used in multiple locations (e.g., success green in chip DONE state and guidance success message)
- **THEN** both locations SHALL reference the same named constant

### Requirement: Dark mode contrast
The WorkflowActionPanel SHALL ensure all text and visual indicators have sufficient contrast against the Darcula theme background.

#### Scenario: DONE chip text readable in dark mode
- **WHEN** a pipeline chip is in DONE state and the dark theme is active
- **THEN** the green text color SHALL have sufficient contrast against the transparent chip background on the dark tool window surface

#### Scenario: BLOCKED chip text readable in dark mode
- **WHEN** a pipeline chip is in BLOCKED state and the dark theme is active
- **THEN** the gray text color SHALL be lighter than `JBColor.GRAY` to ensure readability against the dark background

#### Scenario: Guidance watching text readable in dark mode
- **WHEN** the guidance watching label is displayed in dark theme
- **THEN** the italic gray text SHALL be visible against the dark panel background

### Requirement: Three-tier font size hierarchy
The WorkflowActionPanel SHALL use a consistent three-tier font hierarchy to establish visual scanning order.

#### Scenario: Primary tier for change name and result messages
- **WHEN** the change name label or a success/failure result message is displayed
- **THEN** the text SHALL use 13f Bold font

#### Scenario: Secondary tier for pipeline and progress
- **WHEN** pipeline chip labels or task progress text is displayed
- **THEN** the text SHALL use 12f Plain font

#### Scenario: Tertiary tier for guidance and hints
- **WHEN** guidance watching text, next-artifact tips, elapsed time, or task hints are displayed
- **THEN** the text SHALL use 11f Plain or Italic font

### Requirement: HiDPI-aware spacing
The WorkflowActionPanel SHALL use `JBUI.scale()` for all spacing values so the layout scales correctly on HiDPI displays.

#### Scenario: Section padding uses scaled values
- **WHEN** padding is applied between panel sections
- **THEN** the padding values SHALL be wrapped in `JBUI.scale()` calls

#### Scenario: FlowLayout gaps use scaled values
- **WHEN** FlowLayout is used for pipeline chips, buttons, or guidance button rows
- **THEN** the horizontal and vertical gap values SHALL be wrapped in `JBUI.scale()` calls

#### Scenario: Vertical struts use scaled values
- **WHEN** `Box.createVerticalStrut()` is used for spacing between components
- **THEN** the strut size SHALL be wrapped in `JBUI.scale()`

### Requirement: Section padding via compound borders
The WorkflowActionPanel SHALL apply vertical padding to each logical section using compound borders rather than standalone strut components, so hidden sections do not leave orphaned whitespace.

#### Scenario: Pipeline section has top padding
- **WHEN** the pipeline row is displayed
- **THEN** it SHALL have top padding via a compound border combining the separator line with empty space

#### Scenario: Guidance section has top padding
- **WHEN** the guidance panel is displayed
- **THEN** it SHALL have top padding via a compound border combining the separator line with empty space

#### Scenario: Hiding a section removes its padding
- **WHEN** a section with a compound border is hidden via `setVisible(false)`
- **THEN** neither its separator line nor its padding SHALL be rendered
