## ADDED Requirements

### Requirement: Retired or unavailable model errors

When a provider answers with its documented model-not-found error, the plugin SHALL suggest that the model is retired or not available to this key, and SHALL say where to choose another model. The provider's own message, which names the model, SHALL be kept. This applies to every Direct API surface.

#### Scenario: OpenAI model not found
- **WHEN** OpenAI returns `error.code` `model_not_found`, with HTTP 404 or any other status
- **THEN** the suggestion SHALL say the model is retired or not available to this key and SHALL point at the Direct API model setting and the tool window's model selector

#### Scenario: Gemini model not found
- **WHEN** Gemini returns HTTP 404 with `error.status` `NOT_FOUND` and a message naming the model (`models/…`)
- **THEN** the suggestion SHALL be the same model-not-found suggestion

#### Scenario: Claude model not found
- **WHEN** Claude returns HTTP 404 with `error.type` `not_found_error` and a message that does not name a workspace
- **THEN** the suggestion SHALL be the same model-not-found suggestion

#### Scenario: Claude workspace not found keeps its message
- **WHEN** Claude returns HTTP 404 with `error.type` `not_found_error` and a message that starts with "Workspace"
- **THEN** the plugin SHALL show the workspace-not-found message as today, and SHALL NOT give the model-not-found suggestion

#### Scenario: A retired model and an unknown id read the same
- **WHEN** the saved model is one the provider has retired, or an id that never existed
- **THEN** the plugin SHALL give the same model-not-found suggestion, because the providers answer both the same way

#### Scenario: A 404 without the model-not-found field
- **WHEN** a provider returns HTTP 404 whose body carries no model-not-found field
- **THEN** the plugin SHALL NOT give the model-not-found suggestion

#### Scenario: A Gemini 404 for something other than a model
- **WHEN** Gemini returns HTTP 404 with `error.status` `NOT_FOUND` and a message that names no model
- **THEN** the plugin SHALL NOT give the model-not-found suggestion
