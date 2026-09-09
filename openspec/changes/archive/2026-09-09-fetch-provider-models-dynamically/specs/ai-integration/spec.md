## MODIFIED Requirements

### Requirement: AI providers

The plugin SHALL support Claude, OpenAI, and Gemini as Direct API providers with secure credential storage via IntelliJ PasswordSafe. API keys SHALL be transmitted via provider-specific headers (`x-api-key` for Claude, `Authorization: Bearer ...` for OpenAI, `x-goog-api-key` for Gemini) and SHALL NOT appear in request URLs or query strings.

The plugin SHALL call each provider using a valid, current API contract. Specifically: the default model for each provider SHALL be a valid current model identifier (Claude defaults SHALL be dateless aliases so they resolve to the current snapshot and do not carry a fabricated release date); the Anthropic requests SHALL send the current required `anthropic-version` value (`2023-06-01`); and OpenAI requests SHALL send the token-limit parameter the target model requires (`max_completion_tokens` for the reasoning family — `o1`/`o3`/`o4`/`gpt-5` — and `max_tokens` otherwise). The provider response parsers SHALL be contract-tested against captured provider example responses. When live model discovery (see "Dynamic model discovery") is unavailable, the model selector is drawn from a small static fallback list, and this valid-current-default requirement governs that fallback.

#### Scenario: API generation
- **WHEN** a user configures a Direct API provider with a valid key
- **THEN** the plugin SHALL generate artifacts by calling the provider's API directly

#### Scenario: Test connection
- **WHEN** the user clicks "Test Connection"
- **THEN** the plugin SHALL verify the key by requesting the provider's model list (a read-only call that incurs no generation/token cost) and SHALL display success — populating the model selector from the returned list — or a provider-specific error message

#### Scenario: Gemini auth header
- **WHEN** the plugin builds a Gemini API request
- **THEN** the API key SHALL be set as the `x-goog-api-key` request header AND the request URL SHALL NOT contain a `?key=` query parameter (or any other form of the key)

#### Scenario: Valid provider defaults
- **WHEN** a user selects a provider without choosing an explicit model, and live discovery is unavailable
- **THEN** the fallback default model SHALL be a valid current model identifier (for Claude, a dateless alias with no fabricated release-date suffix) so the first API call does not fail with a model-not-found error

#### Scenario: Anthropic version header
- **WHEN** the plugin builds a Claude API request
- **THEN** the request SHALL carry `anthropic-version: 2023-06-01`

#### Scenario: OpenAI reasoning-model token parameter
- **WHEN** the plugin builds an OpenAI request for a reasoning-family model (`o1`/`o3`/`o4`/`gpt-5`)
- **THEN** the request SHALL send `max_completion_tokens` rather than `max_tokens`; for non-reasoning chat models the request SHALL send `max_tokens`

#### Scenario: Response parser contract
- **WHEN** a provider returns its documented response shape
- **THEN** the plugin SHALL extract the generated text from that shape, verified by a contract test that drives each provider's parser from a captured provider example response

## ADDED Requirements

### Requirement: Dynamic model discovery

When a Direct API provider key is configured, the plugin SHALL populate the model selector from the provider's **live model list**, fetched with the configured key via that provider's list-models endpoint (`GET /v1/models` for OpenAI and Anthropic; `GET /v1beta/models` for Gemini) using the same auth headers as generation. The fetch SHALL run off the EDT (it is a network call). The model selector SHALL remain **editable** so a user can enter a model the list does not include. When no key is configured, the plugin is offline, or the fetch fails, the plugin SHALL **fall back to a static list** of current defaults (governed by "AI providers") rather than presenting an empty selector. Where a provider marks generation capability on each model (Gemini's `supportedGenerationMethods`), the populated list SHALL be filtered to models that support the generation method the plugin uses. Each provider's list-models response parser SHALL be contract-tested against a captured provider example response.

#### Scenario: Populate from the account's live model list
- **WHEN** a valid key is present and the model list is requested (via Test or a refresh)
- **THEN** the plugin SHALL fetch the provider's model list with that key and populate the model selector with the returned models

#### Scenario: Filter to generation-capable models
- **WHEN** the provider reports per-model generation capabilities (e.g. Gemini's `supportedGenerationMethods`)
- **THEN** the populated list SHALL include only models that support the generation method the plugin uses

#### Scenario: Fallback when discovery is unavailable
- **WHEN** no key is configured, the plugin is offline, or the list-models fetch fails
- **THEN** the model selector SHALL fall back to the static default list and SHALL NOT be left empty

#### Scenario: Discovery fetch runs off the EDT
- **WHEN** the plugin fetches the provider's model list
- **THEN** the network call SHALL run on a background thread and the selector update SHALL be posted back to the EDT

#### Scenario: Manual override preserved
- **WHEN** a user types a model identifier not present in the fetched list
- **THEN** the plugin SHALL use the entered value (the selector remains editable)
