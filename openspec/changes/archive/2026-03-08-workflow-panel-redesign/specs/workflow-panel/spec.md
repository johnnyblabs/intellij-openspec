## MODIFIED Requirements

### Requirement: Artifact Pipeline Visualization

The WorkflowActionPanel SHALL display a compact pipeline status row showing the state of each artifact in the change's DAG, using content-aware status that accounts for scaffolding detection. Each chip SHALL be a `JPanel` with tooltip support, click-to-open for completed artifacts, and a highlighted border on the current ready artifact.

#### Scenario: Pipeline with mixed states
- **WHEN** the selected change has artifacts in done, ready, and blocked states (after scaffolding override)
- **THEN** the pipeline row SHALL show each artifact as an interactive chip with a visual state indicator (checkmark for done, filled circle for ready, empty circle for blocked), a text label, and a tooltip describing the artifact's role

#### Scenario: Pipeline updates after generation
- **WHEN** an artifact generation completes
- **THEN** the pipeline row SHALL refresh to show the updated states

#### Scenario: Scaffolded artifacts show as not done
- **WHEN** an artifact file exists but contains only scaffolding placeholder content
- **THEN** the pipeline chip SHALL show the artifact as ready or blocked (not done)

#### Scenario: Current step is visually prominent
- **WHEN** the pipeline contains a READY artifact
- **THEN** the chip for that artifact SHALL be visually highlighted with a distinct border or background color to indicate it is the current step

### Requirement: Post-Generation Guidance Card

After a clipboard or editor-tab delivery, the WorkflowActionPanel SHALL display inline guidance beneath the pipeline (not as a full-card replacement) with tool-specific instructions, a file-watching status indicator, and a "Copy again" button. The pipeline chips SHALL remain visible so users retain context of where they are.

#### Scenario: Clipboard delivery with CLI tool selected
- **WHEN** an artifact prompt is copied to the clipboard and the selected tool is CLI-based (e.g., Claude Code)
- **THEN** the panel SHALL show inline guidance: a confirmation message, tool-specific instructions, and "Watching for {outputPath}..." status with a "Copy again" button
- **AND** the pipeline chips SHALL remain visible above the guidance text

#### Scenario: Clipboard delivery with IDE panel tool selected
- **WHEN** an artifact prompt is copied to the clipboard and the selected tool is IDE-panel-based (e.g., Copilot)
- **THEN** the panel SHALL show inline guidance with manual save instructions and the expected output path
- **AND** the pipeline chips SHALL remain visible above the guidance text

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

#### Scenario: Dynamic tool name from preference
- **WHEN** a preferred tool has been set in project settings
- **THEN** the guidance SHALL display that tool's name
- **WHEN** no preferred tool is set but tools are detected
- **THEN** the guidance SHALL display the first detected tool's name
- **WHEN** no tools are detected
- **THEN** the guidance SHALL display "your AI tool"
