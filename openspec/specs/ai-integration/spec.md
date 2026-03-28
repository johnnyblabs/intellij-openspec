# AI Integration

## Purpose
AI provider configuration, tool detection, delivery method routing, and credential management.

## Requirements

### Requirement: AI providers

The plugin SHALL support Claude, OpenAI, and Gemini as Direct API providers with secure credential storage via IntelliJ PasswordSafe.

#### Scenario: API generation
- **WHEN** a user configures a Direct API provider with a valid key
- **THEN** the plugin SHALL generate artifacts by calling the provider's API directly

#### Scenario: Test connection
- **WHEN** the user clicks "Test Connection"
- **THEN** the plugin SHALL send a test prompt and display success or a provider-specific error message

### Requirement: Delivery method routing

The plugin SHALL support three delivery methods (clipboard, editor tab, Direct API) with smart default selection based on detected tools and configured providers. The Direct API configuration state SHALL additionally gate the availability of Fast-Forward, since FF depends on Direct API for its end-to-end artifact generation workflow.

#### Scenario: Resolution chain
- **WHEN** determining the delivery method
- **THEN** the plugin SHALL check: user preference → configured API → detected tools → generic clipboard fallback

#### Scenario: Direct API gates FF availability
- **WHEN** Direct API is not configured (no provider selected or no API key)
- **THEN** the FF action and FF panel link SHALL be unavailable

### Requirement: Tool-specific guidance

The plugin SHALL provide tool-specific delivery guidance (chat panel name, paste instructions, slash command prefix) for each detected AI tool.

#### Scenario: Post-delivery guidance
- **WHEN** the user generates via clipboard or editor for an IDE panel tool
- **THEN** the plugin SHALL display tool-specific instructions (e.g., "Open Copilot Chat and paste the prompt")

### Requirement: Settings panel

The plugin SHALL organize settings into distinct sections: CLI detection, general options, delivery preferences, and Direct API configuration with provider/model selection.

#### Scenario: Settings layout
- **WHEN** the user opens Settings → Tools → OpenSpec
- **THEN** they SHALL see organized sections with CLI status, delivery dropdown, and API configuration
