## ADDED Requirements

### Requirement: Tool-specific guidance metadata

The AiToolDetectionService SHALL provide guidance metadata for each known AI tool, including the tool's chat panel name, paste action description, slash command prefix (if supported), and whether the tool can auto-save files.

#### Scenario: Copilot guidance metadata
- **WHEN** the tool name is "GitHub Copilot"
- **THEN** the guidance SHALL include chatPanelName "Copilot Chat", promptPrefix "/opsx-", and canAutoSave false

#### Scenario: Claude Code guidance metadata
- **WHEN** the tool name is "Claude Code"
- **THEN** the guidance SHALL include chatPanelName "terminal", promptPrefix "/opsx:", and canAutoSave true

#### Scenario: Cursor guidance metadata
- **WHEN** the tool name is "Cursor"
- **THEN** the guidance SHALL include chatPanelName "Composer", promptPrefix null, and canAutoSave false

#### Scenario: Unknown tool fallback
- **WHEN** the tool name is not in the known tool list
- **THEN** the guidance SHALL return a generic fallback with chatPanelName "your AI tool", no promptPrefix, and canAutoSave false

### Requirement: IDE panel Generate guidance

The workflow panel SHALL show tool-specific step-by-step guidance after a Generate action is delivered via clipboard for an IDE panel tool.

#### Scenario: Copilot clipboard Generate guidance
- **WHEN** a Generate action is delivered via clipboard with GitHub Copilot selected
- **THEN** the guidance SHALL show "Open Copilot Chat and paste the prompt"
- **AND** SHALL show the full save path for the generated artifact

#### Scenario: Cursor clipboard Generate guidance
- **WHEN** a Generate action is delivered via clipboard with Cursor selected
- **THEN** the guidance SHALL show "Open Composer and paste the prompt"
- **AND** SHALL show the full save path for the generated artifact

#### Scenario: CLI tool clipboard Generate guidance unchanged
- **WHEN** a Generate action is delivered via clipboard with a CLI tool selected
- **THEN** the guidance SHALL show the existing message "Paste into <tool> — it will save automatically."

### Requirement: Slash command hint

The workflow panel SHALL show a slash command hint after Generate delivery when the selected tool supports prompt commands.

#### Scenario: Copilot slash command hint
- **WHEN** a Generate action is delivered for a tool with promptPrefix "/opsx-"
- **THEN** the guidance SHALL include a tip: "Tip: You can also use /opsx-propose directly in Copilot Chat"

#### Scenario: No slash command hint for tools without prompts
- **WHEN** a Generate action is delivered for a tool with no promptPrefix
- **THEN** no slash command hint SHALL be shown

### Requirement: IDE panel save path guidance

The workflow panel SHALL show the full file path where the user needs to save the AI tool's response when using an IDE panel tool.

#### Scenario: Generate save path for IDE panel tool
- **WHEN** a Generate action is delivered via clipboard for an IDE panel tool
- **THEN** the guidance SHALL show "Save the response to: <changeDir>/<outputPath>"

#### Scenario: No save path for CLI tools
- **WHEN** a Generate action is delivered via clipboard for a CLI tool
- **THEN** no explicit save path guidance SHALL be shown (CLI tools auto-save)
