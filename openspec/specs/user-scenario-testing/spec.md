### Requirement: Corporate Copilot with skills disabled scenario

The test suite SHALL verify that the plugin delivers a functional experience when the user has GitHub Copilot detected but skills are disabled and no Direct API is configured.

#### Scenario: Delivery resolves to clipboard for IDE panel tool
- **WHEN** AiToolDetectionService detects only "GitHub Copilot" and no API key is configured
- **THEN** DeliveryMethodResolver SHALL resolve to CLIPBOARD mode with label "Copy for GitHub Copilot"

#### Scenario: Tool guidance provides correct paste instructions
- **WHEN** the selected tool is "GitHub Copilot"
- **THEN** ToolGuidance SHALL return chatPanelName "Copilot Chat", pasteAction "Open Copilot Chat and paste the prompt", promptPrefix "/opsx-", and canAutoSave false

#### Scenario: Generate All button is hidden without API
- **WHEN** only IDE panel tools are detected and no API key is configured
- **THEN** the Generate All button visibility logic SHALL return false

### Requirement: Corporate Copilot with Direct API scenario

The test suite SHALL verify that adding a Direct API key unlocks the full Generate All experience for corporate users with skills disabled.

#### Scenario: Direct API overrides clipboard when configured
- **WHEN** AiToolDetectionService detects "GitHub Copilot" and an API key is configured for Claude
- **THEN** DeliveryMethodResolver SHALL resolve to DIRECT_API mode when no saved preference exists

#### Scenario: Generate All button visible with API configured
- **WHEN** Direct API is configured and 2 or more artifacts remain
- **THEN** the Generate All visibility check SHALL return true

### Requirement: CLI power user scenario

The test suite SHALL verify correct behavior when only CLI tools are detected without Direct API.

#### Scenario: Clipboard delivery with CLI tool guidance
- **WHEN** AiToolDetectionService detects "Claude Code" and no API key is configured
- **THEN** DeliveryMethodResolver SHALL resolve to CLIPBOARD mode with label "Copy for Claude Code"

#### Scenario: CLI tool guidance enables auto-save
- **WHEN** the selected tool is "Claude Code"
- **THEN** ToolGuidance SHALL return canAutoSave true and chatPanelName "terminal"

### Requirement: API-only user scenario

The test suite SHALL verify correct behavior when no tools are detected but Direct API is configured.

#### Scenario: Direct API delivery with no tools
- **WHEN** no AI tools are detected and an OpenAI API key is configured
- **THEN** DeliveryMethodResolver SHALL resolve to DIRECT_API mode with label "Generate via OpenAI"

#### Scenario: Generate All visible for API-only user
- **WHEN** no tools are detected, API is configured, and 2+ artifacts remain
- **THEN** Generate All visibility SHALL return true

### Requirement: Zero configuration scenario

The test suite SHALL verify the fallback experience when no tools are detected and no API is configured.

#### Scenario: Bare clipboard fallback
- **WHEN** no AI tools are detected and no API key is configured
- **THEN** DeliveryMethodResolver SHALL resolve to CLIPBOARD mode with label "Copy to Clipboard"

#### Scenario: Generate All hidden with zero config
- **WHEN** no tools and no API are configured
- **THEN** Generate All visibility SHALL return false

### Requirement: Multi-tool scenario

The test suite SHALL verify correct behavior when multiple tools and Direct API are available simultaneously.

#### Scenario: Direct API preferred when API key exists
- **WHEN** both Claude Code and Copilot are detected and a Claude API key is configured
- **THEN** DeliveryMethodResolver SHALL resolve to DIRECT_API when no saved preference exists

#### Scenario: Saved preference overrides automatic resolution
- **WHEN** saved preference is CLIPBOARD and an API key is configured
- **THEN** DeliveryMethodResolver SHALL resolve to CLIPBOARD mode regardless of API availability

### Requirement: DeliveryMethodResolver priority chain coverage

The test suite SHALL verify each step of the DeliveryMethodResolver priority chain in isolation.

#### Scenario: Saved preference takes highest priority
- **WHEN** saved preference is EDITOR_TAB, API key exists, and tools are detected
- **THEN** resolve() SHALL return EDITOR_TAB mode

#### Scenario: API provider takes second priority
- **WHEN** no saved preference exists and Claude provider is configured
- **THEN** resolve() SHALL return DIRECT_API mode

#### Scenario: Detected tools take third priority
- **WHEN** no saved preference exists, no API key, and Copilot is detected
- **THEN** resolve() SHALL return CLIPBOARD mode with tool-specific label

#### Scenario: Bare fallback takes lowest priority
- **WHEN** no saved preference, no API key, and no tools detected
- **THEN** resolve() SHALL return CLIPBOARD with generic label "Copy to Clipboard"

#### Scenario: Invalid saved preference falls through
- **WHEN** saved preference contains an invalid value (not a valid DeliveryMode)
- **THEN** resolve() SHALL ignore the invalid preference and continue to the next priority step

### Requirement: ToolGuidance metadata completeness

The test suite SHALL verify that all 6 known tools and the default fallback return correct ToolGuidance records.

#### Scenario: All known tools have guidance metadata
- **WHEN** getToolGuidance() is called for each of "Claude Code", "Gemini", "GitHub Copilot", "Cursor", "Windsurf", "Cline"
- **THEN** each SHALL return a non-null ToolGuidance with non-empty chatPanelName and pasteAction

#### Scenario: CLI tools report canAutoSave true
- **WHEN** getToolGuidance() is called for "Claude Code" or "Gemini"
- **THEN** canAutoSave SHALL be true

#### Scenario: IDE panel tools report canAutoSave false
- **WHEN** getToolGuidance() is called for "GitHub Copilot", "Cursor", "Windsurf", or "Cline"
- **THEN** canAutoSave SHALL be false

#### Scenario: Default guidance returned for unknown tool
- **WHEN** getToolGuidance() is called with null, empty string, or unknown tool name
- **THEN** the returned ToolGuidance SHALL have chatPanelName "your AI tool", promptPrefix null, and canAutoSave false

#### Scenario: Prompt prefix correctness
- **WHEN** getToolGuidance() is called for "Claude Code"
- **THEN** promptPrefix SHALL be "/opsx:"
- **WHEN** getToolGuidance() is called for "GitHub Copilot"
- **THEN** promptPrefix SHALL be "/opsx-"
- **WHEN** getToolGuidance() is called for "Cursor", "Windsurf", or "Cline"
- **THEN** promptPrefix SHALL be null
