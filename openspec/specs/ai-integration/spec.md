# AI Integration

## Purpose
AI provider configuration, tool detection, delivery method routing, and credential management.

## Requirements

### Requirement: AI providers

The plugin SHALL support Claude, OpenAI, and Gemini as Direct API providers with secure credential storage via IntelliJ PasswordSafe. API keys SHALL be transmitted via provider-specific headers (`x-api-key` for Claude, `Authorization: Bearer ...` for OpenAI, `x-goog-api-key` for Gemini) and SHALL NOT appear in request URLs or query strings.

#### Scenario: API generation
- **WHEN** a user configures a Direct API provider with a valid key
- **THEN** the plugin SHALL generate artifacts by calling the provider's API directly

#### Scenario: Test connection
- **WHEN** the user clicks "Test Connection"
- **THEN** the plugin SHALL send a test prompt and display success or a provider-specific error message

#### Scenario: Gemini auth header
- **WHEN** the plugin builds a Gemini API request
- **THEN** the API key SHALL be set as the `x-goog-api-key` request header AND the request URL SHALL NOT contain a `?key=` query parameter (or any other form of the key)

### Requirement: Delivery method routing

The plugin SHALL support three delivery methods (clipboard, editor tab, Direct API) with smart default selection based on detected tools and configured providers. The Direct API configuration state SHALL additionally gate the availability of Fast-Forward, since FF depends on Direct API for its end-to-end artifact generation workflow.

#### Scenario: Resolution chain
- **WHEN** determining the delivery method
- **THEN** the plugin SHALL check: user preference → configured API → detected tools → generic clipboard fallback

#### Scenario: Direct API gates FF availability
- **WHEN** Direct API is not configured (no provider selected or no API key)
- **THEN** the FF action and FF panel link SHALL be unavailable

### Requirement: Tool-specific guidance

The plugin SHALL provide tool-specific delivery guidance (chat panel name, paste instructions, slash command prefix) for each detected AI tool. Tools classified as `IDE_PANEL` SHALL receive panel-specific copy ("Open <Panel> and paste the prompt"); tools classified as `CLI` SHALL receive terminal-paste copy ("Paste into <Tool>"). Tools without an explicit `TOOL_GUIDANCE` entry SHALL fall back to a generic default.

#### Scenario: Post-delivery guidance
- **WHEN** the user generates via clipboard or editor for an IDE panel tool
- **THEN** the plugin SHALL display tool-specific instructions (e.g., "Open Copilot Chat and paste the prompt")

#### Scenario: Terminal CLI tool guidance
- **WHEN** the user generates via clipboard or editor for a tool classified as `CLI` that has an explicit `TOOL_GUIDANCE` entry (e.g., Claude Code, Gemini, Codex, OpenCode, ForgeCode, Bob Shell)
- **THEN** the plugin SHALL display "Paste into <Tool>" copy and identify the chat-panel name as "terminal"

#### Scenario: ForgeCode and Bob Shell explicit guidance
- **WHEN** the user generates for ForgeCode or Bob Shell
- **THEN** the lookup SHALL return a `ToolGuidance` with `chatPanelName == "terminal"` and `pasteAction == "Paste into ForgeCode"` or `"Paste into Bob Shell"` respectively, NOT the `DEFAULT_GUIDANCE` placeholder

#### Scenario: Junie explicit guidance with slash-command prefix
- **WHEN** the user generates for Junie
- **THEN** the lookup SHALL return a `ToolGuidance` with `chatPanelName == "Junie"`, `pasteAction == "Open Junie and paste the prompt"`, and `promptPrefix == "/opsx-"` matching JetBrains' documented slash-command convention

#### Scenario: Lingma explicit guidance without slash prefix
- **WHEN** the user generates for Lingma
- **THEN** the lookup SHALL return a `ToolGuidance` with `chatPanelName == "Lingma chat"`, `pasteAction == "Open Lingma chat and paste the prompt"`, and `promptPrefix == null` (file-based slash-command discovery is not confirmed by Alibaba's Lingma documentation, so the prompt is delivered verbatim)

#### Scenario: Default fallback when no explicit entry exists
- **WHEN** the user generates for a tool with no `TOOL_GUIDANCE` entry (e.g., any future tool not yet wired up)
- **THEN** the plugin SHALL return `DEFAULT_GUIDANCE` ("your AI tool" / "Paste into your AI tool") rather than throwing or returning null

### Requirement: Settings panel

The plugin SHALL organize settings into distinct sections: CLI detection, general options, delivery preferences, and Direct API configuration with provider/model selection.

#### Scenario: Settings layout
- **WHEN** the user opens Settings → Tools → OpenSpec
- **THEN** they SHALL see organized sections with CLI status, delivery dropdown, and API configuration
