# ai-integration (delta)

## MODIFIED Requirements

### Requirement: Operation-time model selection

The AI model SHALL be selectable at operation time from the OpenSpec tool window, not only from Settings. The tool window SHALL present a model selector alongside the delivery-mode selector; the selector SHALL be **editable** (a user MAY enter a model not in the list) and SHALL be populated from the provider's live model list (see "Dynamic model discovery") with the static default list as a fallback so it is never empty and needs no network to display. The selector SHALL be shown and enabled **only when the active delivery mode is Direct API** (the model has no effect for Clipboard or Editor-Tab delivery). Choosing a model SHALL persist to the configured model setting, and subsequent Direct API operations (generate, Fast-Forward, Continue, Explore) SHALL use the selected model. Selecting a model SHALL NOT interrupt an operation with a modal prompt — the one-click generation flow is preserved. Any live model fetch triggered by the selector SHALL run off the EDT.

The tool window SHALL make the Direct API delivery option — and therefore this model selector — available when a provider is configured **at runtime** (after Settings is applied, or the Setup Wizard finishes), not only when the provider was configured before the tool window was opened. A runtime configuration change SHALL surface the option without requiring an IDE restart or project reopen, and SHALL NOT change the user's currently selected delivery method.

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
