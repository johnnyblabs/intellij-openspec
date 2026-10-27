## MODIFIED Requirements

### Requirement: AI providers

The plugin SHALL support Claude, OpenAI, and Gemini as Direct API providers, storing credentials securely through IntelliJ PasswordSafe. API keys SHALL be sent in provider-specific headers: `Authorization: Bearer ...` for Claude and OpenAI, and `x-goog-api-key` for Gemini. Keys SHALL NOT appear in request URLs or query strings.

For Claude, the plugin SHALL offer an optional **Workspace ID** setting.
- When it is set, every Claude request (list-models and generation) SHALL carry it as the `anthropic-workspace-id` header. When it is empty, the header SHALL be omitted.
- The setting SHALL be shown only when Claude is the selected provider.
- It SHALL be validated as a workspace identifier (the `wrkspc_` prefix).
- It is not a secret. It SHALL be stored as a regular setting, never in PasswordSafe, and SHALL never be written to any OpenSpec project file.

For OpenAI, the plugin SHALL offer optional **Organization ID** and **Project ID** settings.
- When the Organization ID is set, every OpenAI request (list-models and generation) SHALL carry it as the `OpenAI-Organization` header; when the Project ID is set, as the `OpenAI-Project` header. A blank field SHALL send no header.
- The settings SHALL be shown only when OpenAI is the selected provider.
- A value that does not start with the expected prefix (`org-` for an organization, `proj_` for a project) SHALL be flagged with a warning, but SHALL still be saved and sent. No other format check SHALL be applied, because OpenAI treats identifier formats as free to change.
- The help text SHALL say the settings are needed only for legacy user API keys or when the user belongs to more than one organization, and where each ID is found in the OpenAI dashboard. It SHALL NOT state how project or service-account keys behave with them.
- They are not secrets. They SHALL be stored as regular settings, never in PasswordSafe, and SHALL never be written to any OpenSpec project file.

The plugin SHALL call each provider using a valid, current API contract:
- The default model for each provider SHALL be a valid current model identifier. Claude defaults SHALL be dateless aliases, so they resolve to the current snapshot and don't carry a fabricated release date.
- Anthropic requests SHALL send the current required `anthropic-version` value (`2023-06-01`).
- OpenAI requests SHALL send `max_tokens` only to the legacy non-reasoning chat families (`gpt-3.5`, `gpt-4`, including `gpt-4o` and `gpt-4.1`, and `chatgpt-4o`), and `max_completion_tokens` to every other model, including the reasoning family (the `o`-series, `gpt-5` and later) and families released after the plugin. A fine-tuned model SHALL follow its base model.
- The provider response parsers SHALL be contract-tested against captured provider responses, and live-captured ones SHALL be preferred where a key is available.

The Claude parser SHALL return the concatenated text of every `text` content block. It SHALL skip `thinking` and `redacted_thinking` blocks, because models with thinking on by default return those before the text. The Gemini parser SHALL return the concatenated text of every part that isn't marked as a thought. Every parser SHALL treat a response with no usable text as an error that names the provider's stated reason (stop or finish reason, refusal, or block reason), and SHALL NOT return empty text as a generated artifact. When no live model list is available (see "Dynamic model discovery"), the model selector falls back to a small static list, and the valid-current-default rule above governs that list.

#### Scenario: API generation
- **WHEN** a user configures a Direct API provider with a valid key
- **THEN** the plugin SHALL generate artifacts by calling the provider's API directly

#### Scenario: Test connection
- **WHEN** the user clicks "Test Connection"
- **THEN** the plugin SHALL verify the key by requesting the provider's model list, a read-only call with no generation or token cost. It SHALL then either show success and populate the model selector from the returned list, or show a provider-specific error message. For Claude, the request SHALL use the Workspace ID currently entered in the settings form; for OpenAI, the Organization ID and Project ID currently entered.

#### Scenario: Gemini auth header
- **WHEN** the plugin builds a Gemini API request
- **THEN** the API key SHALL be set as the `x-goog-api-key` request header AND the request URL SHALL NOT contain a `?key=` query parameter (or any other form of the key)

#### Scenario: Claude auth header
- **WHEN** the plugin builds a Claude API request (list-models or generation)
- **THEN** the API key SHALL be sent as `Authorization: Bearer <key>`, the legacy `x-api-key` header SHALL NOT be sent, and the request URL SHALL NOT contain the key

#### Scenario: Claude workspace header when configured
- **WHEN** a Claude Workspace ID is configured and the plugin builds a Claude list-models or generation request
- **THEN** the request SHALL carry `anthropic-workspace-id` with that value

#### Scenario: Claude workspace header omitted when blank
- **WHEN** no Claude Workspace ID is configured
- **THEN** Claude requests SHALL NOT carry an `anthropic-workspace-id` header, so keys scoped to a single workspace keep working unchanged

#### Scenario: Workspace ID validation
- **WHEN** the user enters a Workspace ID that does not start with `wrkspc_`
- **THEN** the settings form SHALL flag the value as invalid and SHALL NOT send it

#### Scenario: OpenAI organization and project headers when configured
- **WHEN** an OpenAI Organization ID and Project ID are configured and the plugin builds an OpenAI list-models or generation request
- **THEN** the request SHALL carry `OpenAI-Organization` and `OpenAI-Project` with those values

#### Scenario: OpenAI headers omitted when blank
- **WHEN** neither OpenAI field is configured
- **THEN** OpenAI requests SHALL carry neither `OpenAI-Organization` nor `OpenAI-Project`, so they are byte-identical in their headers to requests before these settings existed

#### Scenario: Only the configured OpenAI header is sent
- **WHEN** only the Project ID is configured
- **THEN** OpenAI requests SHALL carry `OpenAI-Project` and SHALL NOT carry `OpenAI-Organization`

#### Scenario: Unexpected OpenAI ID prefix
- **WHEN** the user enters an Organization ID that does not start with `org-`, or a Project ID that does not start with `proj_`
- **THEN** the settings form SHALL show a warning for that field, and the value SHALL still be saved and sent as entered (trimmed)

#### Scenario: OpenAI settings only for OpenAI
- **WHEN** the selected provider is Claude or Gemini
- **THEN** the Organization ID and Project ID fields SHALL be hidden, and requests to that provider SHALL carry neither OpenAI header

#### Scenario: Valid provider defaults
- **WHEN** a user selects a provider without choosing an explicit model, and live discovery is unavailable
- **THEN** the fallback default model SHALL be a valid current model identifier (for Claude, a dateless alias with no fabricated release-date suffix) so the first API call does not fail with a model-not-found error

#### Scenario: Anthropic version header
- **WHEN** the plugin builds a Claude API request
- **THEN** the request SHALL carry `anthropic-version: 2023-06-01`

#### Scenario: OpenAI reasoning-model token parameter
- **WHEN** the plugin builds an OpenAI request for a reasoning-family model (for example `o1`, `o4-mini`, `gpt-5`, `gpt-6-luna`)
- **THEN** the request SHALL send `max_completion_tokens` rather than `max_tokens`; for the legacy chat families (for example `gpt-4o`, `gpt-3.5-turbo`) the request SHALL send `max_tokens`

#### Scenario: Response parser contract
- **WHEN** a provider returns its response shape
- **THEN** the plugin SHALL extract the generated text from that shape. A contract test SHALL verify this by driving each provider's parser from a captured provider response, live-captured for Claude.

#### Scenario: Claude response with a leading thinking block
- **WHEN** a Claude response's content starts with a `thinking` or `redacted_thinking` block followed by one or more `text` blocks
- **THEN** the plugin SHALL return the concatenated text of the `text` blocks and SHALL NOT fail

#### Scenario: Claude response with no text block
- **WHEN** a Claude response contains no `text` block (for example, the token limit was reached while thinking)
- **THEN** the plugin SHALL report a clear error that names the response's `stop_reason`, and SHALL NOT fail with an unhandled exception

#### Scenario: Gemini response with several parts
- **WHEN** a Gemini candidate's content has several parts, some marked `thought: true`
- **THEN** the plugin SHALL return the concatenated text of the parts that are not thoughts

#### Scenario: Response with no usable text
- **WHEN** a Gemini candidate has no content or no text parts, or an OpenAI choice's `content` is null or empty
- **THEN** the plugin SHALL report an error naming the reason the provider gives (Gemini `finishReason` or `promptFeedback.blockReason`; OpenAI `refusal` or `finish_reason`), and SHALL NOT write an empty artifact

#### Scenario: Fallback lists contain no retired models
- **WHEN** the static fallback lists are checked against the providers' published deprecation pages
- **THEN** no listed model SHALL be retired, and no default SHALL be a deprecated model when a non-deprecated replacement is published

## ADDED Requirements

### Requirement: OpenAI organization and project errors

When OpenAI rejects a request as unauthenticated (HTTP 401) and an Organization ID or Project ID is configured, the plugin SHALL keep OpenAI's own error message and SHALL suggest checking that the configured IDs belong to this API key, in addition to checking the key. This applies to both Test and generation. The plugin SHALL NOT infer the cause from the error body.

#### Scenario: 401 with an OpenAI ID configured
- **WHEN** OpenAI returns HTTP 401 and an Organization ID or a Project ID is configured
- **THEN** the error SHALL show OpenAI's message and a suggestion to check the API key and that the Organization and Project IDs in the Direct API settings belong to it

#### Scenario: 401 with no OpenAI ID configured
- **WHEN** OpenAI returns HTTP 401 and neither field is configured
- **THEN** the error SHALL be reported as it is today, suggesting only to check the API key
