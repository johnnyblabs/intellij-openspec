## MODIFIED Requirements

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
