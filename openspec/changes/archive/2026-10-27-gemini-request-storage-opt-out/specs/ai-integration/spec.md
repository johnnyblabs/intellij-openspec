## ADDED Requirements

### Requirement: Gemini requests are not stored by Google

Every Gemini generation request the plugin sends SHALL ask Google not to store it, using the request-level `store` field set to false at the top level of the request body. The request-level field takes precedence over the project's own setting, so the opt-out applies whatever the project's logging setting is. The plugin SHALL NOT offer a setting that turns storage back on.

#### Scenario: A generation request opts out
- **WHEN** the plugin sends any Gemini generation request, from Generate, Generate All Remaining, Fast-Forward, Continue, Regenerate, Explore or Verify
- **THEN** the request body SHALL carry `"store": false` at its top level

#### Scenario: Not inside the generation settings
- **WHEN** the plugin builds a Gemini request body
- **THEN** `store` SHALL NOT appear inside `generationConfig`, which Google rejects

#### Scenario: Other providers are unchanged
- **WHEN** the plugin sends a Claude or OpenAI request
- **THEN** the request body SHALL NOT carry a `store` field
