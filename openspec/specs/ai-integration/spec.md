# AI Integration

## Purpose
AI provider configuration, tool detection, delivery method routing, and credential management.

## Requirements

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

### Requirement: Operation-time model selection

The AI model SHALL be selectable at operation time from the OpenSpec tool window, not only from Settings. The tool window SHALL present a model selector alongside the delivery-mode selector; the selector SHALL be **editable** (a user MAY enter a model not in the list) and SHALL be populated from the provider's live model list (see "Dynamic model discovery") with the static default list as a fallback so it is never empty and needs no network to display. The selector SHALL be shown and enabled **only when the active delivery mode is Direct API** (the model has no effect for Clipboard or Editor-Tab delivery). Choosing a model SHALL persist to the configured model setting, and subsequent Direct API operations (generate, Fast-Forward, Continue, Explore) SHALL use the selected model. Selecting a model SHALL NOT interrupt an operation with a modal prompt — the one-click generation flow is preserved. Any live model fetch triggered by the selector SHALL run off the EDT.

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
