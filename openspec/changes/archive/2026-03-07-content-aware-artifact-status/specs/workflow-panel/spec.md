## MODIFIED Requirements

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

### Requirement: Post-Generation Guidance Card

After a clipboard or editor-tab delivery, the WorkflowActionPanel SHALL display a guidance card that confirms what happened, names the detected AI tool, shows the expected output path, indicates the next artifact to generate, and provides clear next-step instructions.

#### Scenario: Clipboard delivery shows guidance
- **WHEN** an artifact prompt is copied to the clipboard
- **THEN** the panel SHALL replace the Generate button area with a guidance card showing: a confirmation message (e.g., "Instructions copied to clipboard"), the detected AI tool name (e.g., "Paste into Claude Code"), the expected output path, and "Copy again" and "Check for updates" buttons

#### Scenario: Editor tab delivery shows guidance
- **WHEN** an artifact prompt is opened in an editor tab
- **THEN** the panel SHALL replace the Generate button area with a guidance card showing: a confirmation message (e.g., "Instructions opened in editor"), the detected AI tool name, the expected output path, and a "Check for updates" button

#### Scenario: Dynamic tool name
- **WHEN** AI tools are detected in the project (e.g., Claude Code, GitHub Copilot, Gemini)
- **THEN** the guidance card SHALL display the primary detected tool name (e.g., "Paste into Claude Code")
- **WHEN** no AI tools are detected
- **THEN** the guidance card SHALL display a generic label (e.g., "Paste into your AI tool")

#### Scenario: Copy again
- **WHEN** the user clicks "Copy again" on the guidance card
- **THEN** the prompt SHALL be re-copied to the clipboard

#### Scenario: Check for updates dismisses card
- **WHEN** the user clicks "Check for updates" on the guidance card
- **THEN** the guidance card SHALL be dismissed, the DAG cache invalidated, and the panel refreshed to show updated artifact status

#### Scenario: Next artifact indicator
- **WHEN** the guidance card is displayed after generating an artifact that is not the last in the pipeline
- **THEN** the guidance card SHALL show which artifact is next (e.g., "Next up: Generate design")
