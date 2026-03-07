## ADDED Requirements

### Requirement: Gemini API Provider

The plugin SHALL support Google Gemini as a Direct API provider for artifact generation.

#### Scenario: Gemini provider selection
- **WHEN** the user selects "Gemini" as the AI provider in Settings
- **THEN** the model dropdown SHALL show Gemini models (gemini-2.5-pro, gemini-2.5-flash)

#### Scenario: Gemini API generation
- **WHEN** the user generates an artifact with Gemini configured
- **THEN** the plugin SHALL call the Gemini REST API and return the generated content

#### Scenario: Gemini connection test
- **WHEN** the user clicks "Test" with Gemini configured
- **THEN** the plugin SHALL verify the API key works and display the connection status
