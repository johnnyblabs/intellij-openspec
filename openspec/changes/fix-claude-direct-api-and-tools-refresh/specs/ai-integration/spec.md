# Spec Delta

## MODIFIED Requirements

### Requirement: AI providers

The plugin SHALL support Claude, OpenAI, and Gemini as Direct API providers, storing credentials securely through IntelliJ PasswordSafe. API keys SHALL be sent in provider-specific headers: `Authorization: Bearer ...` for Claude and OpenAI, and `x-goog-api-key` for Gemini. Keys SHALL NOT appear in request URLs or query strings.

For Claude, the plugin SHALL offer an optional **Workspace ID** setting.
- When it is set, every Claude request (list-models and generation) SHALL carry it as the `anthropic-workspace-id` header. When it is empty, the header SHALL be omitted.
- The setting SHALL be shown only when Claude is the selected provider.
- It SHALL be validated as a workspace identifier (the `wrkspc_` prefix).
- It is not a secret. It SHALL be stored as a regular setting, never in PasswordSafe, and SHALL never be written to any OpenSpec project file.

The plugin SHALL call each provider using a valid, current API contract:
- The default model for each provider SHALL be a valid current model identifier. Claude defaults SHALL be dateless aliases, so they resolve to the current snapshot and don't carry a fabricated release date.
- Anthropic requests SHALL send the current required `anthropic-version` value (`2023-06-01`).
- OpenAI requests SHALL send the token-limit parameter the target model requires: `max_completion_tokens` for the reasoning family (`o1`/`o3`/`o4`/`gpt-5`), and `max_tokens` otherwise.
- The provider response parsers SHALL be contract-tested against captured provider responses, and live-captured ones SHALL be preferred where a key is available.

The Claude parser SHALL return the concatenated text of every `text` content block. It SHALL skip `thinking` and `redacted_thinking` blocks, because models with thinking on by default return those before the text. When no live model list is available (see "Dynamic model discovery"), the model selector falls back to a small static list, and the valid-current-default rule above governs that list.

#### Scenario: API generation
- **WHEN** a user configures a Direct API provider with a valid key
- **THEN** the plugin SHALL generate artifacts by calling the provider's API directly

#### Scenario: Test connection
- **WHEN** the user clicks "Test Connection"
- **THEN** the plugin SHALL verify the key by requesting the provider's model list, a read-only call with no generation or token cost. It SHALL then either show success and populate the model selector from the returned list, or show a provider-specific error message. For Claude, the request SHALL use the Workspace ID currently entered in the settings form.

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
- **WHEN** a provider returns its response shape
- **THEN** the plugin SHALL extract the generated text from that shape. A contract test SHALL verify this by driving each provider's parser from a captured provider response, live-captured for Claude.

#### Scenario: Claude response with a leading thinking block
- **WHEN** a Claude response's content starts with a `thinking` or `redacted_thinking` block followed by one or more `text` blocks
- **THEN** the plugin SHALL return the concatenated text of the `text` blocks and SHALL NOT fail

#### Scenario: Claude response with no text block
- **WHEN** a Claude response contains no `text` block (for example, the token limit was reached while thinking)
- **THEN** the plugin SHALL report a clear error that names the response's `stop_reason`, and SHALL NOT fail with an unhandled exception

## ADDED Requirements

### Requirement: Claude workspace errors

When the Claude API rejects a request because of workspace selection, the plugin SHALL show an actionable message instead of the raw provider error. This applies to both Test and generation.

#### Scenario: Key not scoped to a workspace
- **WHEN** Claude returns HTTP 400 stating that the key is not scoped to a workspace and the request must include `anthropic-workspace-id`
- **THEN** the plugin SHALL tell the user to enter a Workspace ID in the Direct API settings or to use an API key scoped to one workspace

#### Scenario: Workspace ID not recognized
- **WHEN** Claude returns HTTP 400 stating that the `anthropic-workspace-id` header must be a valid workspace ID
- **THEN** the plugin SHALL tell the user that Claude doesn't recognize the configured Workspace ID and where to copy it from, and SHALL NOT report the key as unscoped

#### Scenario: Workspace not found
- **WHEN** Claude returns HTTP 404 stating that the requested workspace was not found
- **THEN** the plugin SHALL tell the user that the configured Workspace ID wasn't found or isn't accessible with this key

### Requirement: Direct API multi-file artifacts

When an artifact's output path is a glob (for example `specs/**/*.md`), Direct API generation SHALL produce one file per marked section of the response instead of writing the glob as a file name. The prompt SHALL state the marker format and supply the context an API call can't look up: existing capability paths, the main specs the proposal names, and the change's existing files for that artifact.

#### Scenario: Specs generated over Direct API
- **WHEN** the user generates the `specs` artifact through Direct API and the response marks one or more files whose paths match the artifact's glob
- **THEN** the plugin SHALL write each marked file at its path inside the change, creating directories as needed, and report the files it wrote

#### Scenario: Prompt supplies what the model can't look up
- **WHEN** the plugin builds a Direct API prompt for an artifact with a glob output path
- **THEN** the prompt SHALL include the file-marker format, the capability paths under `openspec/specs/`, the full text of each main spec whose capability path the proposal names, and the artifact's files that already exist in the change

#### Scenario: Invalid file path in the response
- **WHEN** any marked path is absolute, contains `..`, or does not match the artifact's glob
- **THEN** the plugin SHALL write none of the files and SHALL report which path was rejected

#### Scenario: Response with no marked files
- **WHEN** the response contains no file markers
- **THEN** the plugin SHALL write nothing and SHALL report that the response named no files

#### Scenario: Same writer for every Direct API path
- **WHEN** an artifact is generated through Direct API from the tool window or through Generate All / Fast-Forward
- **THEN** both SHALL write the result through the same writer, so a glob output path is handled identically
