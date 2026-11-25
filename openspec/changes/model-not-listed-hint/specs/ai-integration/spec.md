## ADDED Requirements

### Requirement: A saved model the provider no longer lists is pointed out

When Settings has fetched the provider's live model list successfully and the selected model isn't in it, the plugin SHALL show a hint by the Model field saying the model isn't in the provider's current list for this key, may have been retired or routed to another model, and that a model from the list should be chosen. The hint SHALL name no replacement model, and SHALL show nothing on a failed or absent fetch.

#### Scenario: A model the provider no longer lists
- **WHEN** the Gemini model list is fetched successfully and the selected model is `gemini-2.5-pro`, which the list doesn't include
- **THEN** Settings SHALL show the hint by the Model field, naming `gemini-2.5-pro` and Gemini

#### Scenario: A model in the list
- **WHEN** the selected model is in the fetched list
- **THEN** Settings SHALL show no hint

#### Scenario: Choosing another model
- **WHEN** the hint is showing and the user selects a model from the fetched list
- **THEN** the hint SHALL disappear

#### Scenario: No list to judge by
- **WHEN** no list has been fetched, the fetch failed and the selector shows the static defaults, or the provider, key, Workspace, Org or Project changed since the fetch
- **THEN** Settings SHALL show no hint, because the list says nothing about what the provider now offers this key

#### Scenario: An incomplete list
- **WHEN** the provider's response says more pages exist (`nextPageToken`, or `has_more: true`)
- **THEN** Settings SHALL show no hint

#### Scenario: A fetch that finishes after a provider switch
- **WHEN** the user switches provider while a model-list fetch is running
- **THEN** that fetch SHALL NOT repopulate the selector or produce a hint

#### Scenario: A Claude alias for a dated model
- **WHEN** the selected Claude model is `claude-sonnet-4-5` and the list holds `claude-sonnet-4-5-20250929`
- **THEN** Settings SHALL treat it as listed and show no hint

#### Scenario: A Gemini id written with its prefix
- **WHEN** the selected Gemini model is written `models/gemini-3.8-flash` and the list holds `gemini-3.8-flash`
- **THEN** Settings SHALL treat it as listed and show no hint
