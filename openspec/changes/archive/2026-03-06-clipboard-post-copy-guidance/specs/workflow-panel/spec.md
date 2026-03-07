## ADDED Requirements

### Requirement: Post-Generation Guidance Card

After a clipboard or editor-tab delivery, the WorkflowActionPanel SHALL display a guidance card that confirms what happened, names the detected AI tool, shows the expected output path, and provides clear next-step instructions.

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

## MODIFIED Requirements

### Requirement: Auto-Advance After Generation

The panel SHALL automatically advance to the next ready artifact after a generation completes.

#### Scenario: Direct API generation completes
- WHEN an artifact is successfully generated via Direct API
- THEN the panel SHALL refresh the DAG status and show the next ready artifact

#### Scenario: Clipboard or editor mode completion
- WHEN an artifact prompt is copied to clipboard or opened in editor
- THEN the panel SHALL display a guidance card with next-step instructions and a "Check for updates" button that re-checks artifact status when clicked
