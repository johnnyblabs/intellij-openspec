# Workflow Panel

## Purpose
Guided artifact generation UI with one-click Generate button and smart delivery defaults.

## Requirements

### Requirement: Workflow Action Panel Display

The tool window SHALL display a Workflow Action Panel between the tree and the status bar that shows the selected change's name via a change selector, artifact pipeline status, and generation controls.

#### Scenario: Active change with ready artifacts
- WHEN an active change is selected with ready artifacts
- THEN the panel SHALL display the change selector, a pipeline status row showing each artifact's state, and a "Generate [artifact-name]" button for the next ready artifact

#### Scenario: No active change
- WHEN no active change exists
- THEN the panel SHALL display guidance text such as "No active change" with a hint to use Propose

#### Scenario: All artifacts complete
- WHEN all artifacts for the selected change are done
- THEN the panel SHALL display "All complete" in the pipeline with guidance to apply or archive

### Requirement: Generate Button with Smart Default

The panel SHALL provide a Generate button that uses the user's preferred delivery method by default, with a dropdown to switch methods.

#### Scenario: One-click generation with preferred method
- WHEN the user has a saved preferred delivery method
- THEN clicking the Generate button SHALL trigger generation using that method without showing a dialog

#### Scenario: Split button dropdown
- WHEN the user clicks the dropdown chevron on the Generate button
- THEN a menu SHALL appear listing all available delivery methods (Direct API, Copy for [detected tool], Copy to Clipboard, Open in Editor)

#### Scenario: Method remembered
- WHEN the user selects a delivery method from the dropdown
- THEN that method SHALL be saved as the preferred method for future clicks

### Requirement: Artifact Pipeline Visualization

The WorkflowActionPanel SHALL display a compact pipeline status row showing the state of each artifact in the change's DAG, using content-aware status that accounts for scaffolding detection.

#### Scenario: Pipeline with mixed states
- **WHEN** the selected change has artifacts in done, ready, and blocked states (after scaffolding override)
- **THEN** the pipeline row SHALL show each artifact as a labeled chip with a visual state indicator (e.g., checkmark for done, filled circle for ready, empty circle for blocked)

#### Scenario: Pipeline updates after generation
- **WHEN** an artifact generation completes
- **THEN** the pipeline row SHALL refresh to show the updated states

#### Scenario: Scaffolded artifacts show as not done
- **WHEN** an artifact file exists but contains only scaffolding placeholder content
- **THEN** the pipeline chip SHALL show the artifact as ready or blocked (not done)

### Requirement: Context Menu Generation Routing

When a user triggers generation from the tree context menu on a change node, the panel SHALL select that change and initiate generation.

#### Scenario: Right-click generate on change
- WHEN the user right-clicks a change node and selects "Generate..."
- THEN the panel SHALL set its active change to that change and trigger the Generate button action

### Requirement: Post-Generation Guidance Card

After a clipboard or editor-tab delivery, the WorkflowActionPanel SHALL display a guidance card with tool-specific instructions based on the selected AI tool's type (CLI-based vs IDE-panel).

#### Scenario: Clipboard delivery with CLI tool selected
- **WHEN** an artifact prompt is copied to the clipboard and the selected tool is CLI-based (e.g., Claude Code, Gemini)
- **THEN** the guidance card SHALL show: a confirmation message, the tool name with a note that it can save output automatically (e.g., "Paste into Claude Code — it will save the output automatically"), the expected output path as reference, and "Copy again" and "Check for updates" buttons

#### Scenario: Clipboard delivery with IDE panel tool selected
- **WHEN** an artifact prompt is copied to the clipboard and the selected tool is IDE-panel-based (e.g., GitHub Copilot, Cursor)
- **THEN** the guidance card SHALL show: a confirmation message, the tool name with manual save instructions (e.g., "Paste into GitHub Copilot Chat, copy the response, and save to:"), the expected output path, and "Copy again" and "Check for updates" buttons

#### Scenario: Editor tab delivery shows guidance
- **WHEN** an artifact prompt is opened in an editor tab
- **THEN** the guidance card SHALL show the selected tool name with appropriate instructions and a "Check for updates" button

#### Scenario: Dynamic tool name from preference
- **WHEN** a preferred tool has been set in project settings
- **THEN** the guidance card SHALL display that tool's name
- **WHEN** no preferred tool is set but tools are detected
- **THEN** the guidance card SHALL display the first detected tool's name
- **WHEN** no tools are detected
- **THEN** the guidance card SHALL display "your AI tool"

#### Scenario: Copy again
- **WHEN** the user clicks "Copy again" on the guidance card
- **THEN** the prompt SHALL be re-copied to the clipboard

#### Scenario: Check for updates dismisses card
- **WHEN** the user clicks "Check for updates" on the guidance card
- **THEN** the guidance card SHALL be dismissed, the DAG cache invalidated, and the panel refreshed

#### Scenario: Next artifact indicator
- **WHEN** the guidance card is displayed after generating an artifact that is not the last in the pipeline
- **THEN** the guidance card SHALL show which artifact is next (e.g., "Next up: Generate design")

#### Scenario: Save-path hint in clipboard prompt for CLI tools
- **WHEN** an artifact prompt is copied to the clipboard and the selected tool is CLI-based
- **THEN** the prompt text SHALL include a save-path hint line (e.g., "Save your response to: [path]") appended to the end

### Requirement: Auto-Advance After Generation

The panel SHALL automatically advance to the next ready artifact after a generation completes.

#### Scenario: Direct API generation completes
- WHEN an artifact is successfully generated via Direct API
- THEN the panel SHALL refresh the DAG status and show the next ready artifact

#### Scenario: Clipboard or editor mode completion
- WHEN an artifact prompt is copied to clipboard or opened in editor
- THEN the panel SHALL display a guidance card with next-step instructions and a "Check for updates" button that re-checks artifact status when clicked
