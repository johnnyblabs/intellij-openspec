## MODIFIED Requirements

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
