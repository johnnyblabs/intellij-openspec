## MODIFIED Requirements

### Requirement: AI providers

The plugin SHALL support Claude, OpenAI and Gemini as Direct API providers, storing credentials in IntelliJ PasswordSafe. API keys SHALL be sent in provider-specific headers (`Authorization: Bearer` for Claude and OpenAI, `x-goog-api-key` for Gemini) and SHALL NOT appear in request URLs or query strings. Each provider SHALL be called with a valid, current API contract, and each response parser SHALL be contract-tested against captured provider responses.

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

#### Scenario: Claude Workspace ID
- **WHEN** Claude is the selected provider
- **THEN** the Workspace ID setting SHALL be shown, validated as a `wrkspc_` identifier
- **AND** when set, every Claude request (list-models and generation) SHALL carry it as `anthropic-workspace-id`; when empty, the header SHALL be omitted
- **AND** it SHALL be a regular setting, never in PasswordSafe, and never written to an OpenSpec project file

#### Scenario: OpenAI Organization and Project IDs
- **WHEN** OpenAI is the selected provider
- **THEN** the optional Organization ID and Project ID settings SHALL be shown
- **AND** when set, each request SHALL carry it as `OpenAI-Organization` or `OpenAI-Project`; a blank field SHALL send no header
- **AND** a value without the prefix (`org-`, `proj_`) SHALL be flagged with a warning but still saved and sent
- **AND** the help text SHALL say the IDs are needed only for legacy user API keys or for users in more than one organization, and where to find each ID, without describing project or service-account behaviour
- **AND** they SHALL be regular settings, never in PasswordSafe, and never in project files

#### Scenario: Current API contract
- **WHEN** a provider default is chosen
- **THEN** it SHALL be a valid current model identifier; Claude defaults SHALL be dateless aliases
- **AND** Anthropic requests SHALL send `anthropic-version: 2023-06-01`
- **AND** OpenAI requests SHALL send `max_tokens` only to the legacy non-reasoning families (`gpt-3.5`, `gpt-4`, including `gpt-4o` and `gpt-4.1`, and `chatgpt-4o`), and `max_completion_tokens` to every other model, including the reasoning families and later ones; a fine-tuned model SHALL follow its base model

#### Scenario: Response parsing
- **WHEN** a Claude response is parsed
- **THEN** it SHALL return the concatenated text of every `text` block, skipping `thinking` and `redacted_thinking` blocks
- **WHEN** a Gemini response is parsed
- **THEN** it SHALL return the concatenated text of every part not marked as a thought
- **AND** a response with no usable text SHALL be an error naming the provider's stop, finish, refusal or block reason, and SHALL NOT yield empty text as an artifact
- **AND** when no live model list is available, the selector SHALL fall back to the static default list

### Requirement: Dynamic model discovery

When a provider key is configured, the plugin SHALL populate the model selector from the provider's live model list, fetched with the configured key off the EDT. The selector SHALL remain editable. Without a key, offline, or on a failed fetch, the plugin SHALL fall back to the static defaults rather than an empty selector. Each list-models parser SHALL be contract-tested against a captured provider response.

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

#### Scenario: Default selection prefers the static default
- **WHEN** no model is chosen yet and the fetched list contains the provider's static default model
- **THEN** the selector SHALL select that default rather than the list's first entry, which may be an access-limited or non-chat model

#### Scenario: Filtering and default selection
- **WHEN** a provider marks generation capability per model (Gemini's `supportedGenerationMethods`)
- **THEN** the list SHALL be filtered to models supporting the generation method the plugin uses
- **AND** when no model is chosen, the selection SHALL be the static default if the fetched list contains it, and the first fetched model otherwise

### Requirement: Operation-time model selection

The AI model SHALL be selectable at operation time from the OpenSpec tool window, alongside the delivery-mode selector. The selector SHALL be editable, populated from the live list with the static defaults as fallback, and shown and enabled only when the delivery mode is Direct API. Choosing a model SHALL persist to the model setting and be used by later Direct API operations, without a modal prompt.

#### Scenario: Model is selectable in the tool window
- **WHEN** Direct API is the active delivery mode and a provider key is configured
- **THEN** the tool window SHALL show an editable model selector populated from the account's live model list (falling back to the static default list), and its selection SHALL determine the model used by subsequent Direct API operations

#### Scenario: Selection persists and drives operations without a mid-flow prompt
- **WHEN** the user picks or types a model in the tool-window selector and then triggers a generation (chip generate, Fast-Forward, Continue, or Explore)
- **THEN** the selection SHALL be persisted to the configured model and the operation SHALL use it directly, without prompting the user to choose a model during the operation

#### Scenario: Selector is Direct-API-only
- **WHEN** the active delivery mode is Clipboard or Editor Tab
- **THEN** the tool-window model selector SHALL be hidden or disabled, since no model is used for those delivery modes

#### Scenario: Model fetch runs off the EDT
- **WHEN** the tool-window selector fetches the live model list to populate itself
- **THEN** the network call SHALL run on a background thread and the selector update SHALL be posted back to the EDT

#### Scenario: Direct API surfaces when configured mid-session
- **WHEN** the tool window is already open with no Direct API provider configured, and the user then configures a provider (Settings → Apply, or finishing the Setup Wizard)
- **THEN** the tool window SHALL add the Direct API delivery option — making the model selector available once Direct API is the active delivery mode — without an IDE restart, and SHALL preserve the user's current delivery-method selection

#### Scenario: Runtime configuration
- **WHEN** a provider is configured at runtime (after Settings is applied, or the Setup Wizard finishes)
- **THEN** the Direct API delivery option and this selector SHALL be available without restarting the IDE or reopening the project
- **AND** the selected delivery method SHALL NOT change

#### Scenario: Off-EDT live fetch
- **WHEN** the selector triggers a live model fetch
- **THEN** the fetch SHALL run off the EDT

### Requirement: Direct API error classification

When a Direct API request fails, the plugin SHALL choose its suggestion from the provider's documented error field in the body, and SHALL fall back to the HTTP status only when the body has no recognized field. The provider's own error message SHALL be kept. This applies to Test and every generation path.

#### Scenario: Body field decides over status
- **WHEN** a provider returns HTTP 429 with a body whose code names a quota or spend limit
- **THEN** the plugin SHALL give the quota or spend-limit suggestion, not the rate-limit one

#### Scenario: Body that isn't JSON
- **WHEN** a provider returns an error whose body is not JSON or has no recognized field
- **THEN** the plugin SHALL choose the suggestion from the HTTP status, and SHALL show the start of the body as the message, as today

#### Scenario: JSON body sent as plain text
- **WHEN** OpenAI returns an error with a JSON body and a `text/plain` content type
- **THEN** the plugin SHALL still read the body's error fields

#### Scenario: Fields per provider
- **WHEN** a request fails
- **THEN** Claude errors SHALL be read from `error.type` and `error.details.error_code`, OpenAI errors from `error.code`, `error.type` and `error.param`, and Gemini errors from `error.status` and `details[].reason`
