## MODIFIED Requirements

### Requirement: Key and permission errors per provider

A rejected API key SHALL get the "check your API key" suggestion whatever status the provider uses for it. Gemini rejects an invalid key with HTTP 400 and reason `API_KEY_INVALID`. A Gemini permission error SHALL also get a note that Google rejects or blocks older keys not restricted to the Gemini API, and that a new Google AI Studio key fixes it. Google documents no distinct error for such keys, so the note names a possible cause.

#### Scenario: Gemini invalid key
- **WHEN** Gemini returns HTTP 400 with a `details[].reason` of `API_KEY_INVALID`
- **THEN** the suggestion SHALL be to check the API key in the Direct API settings
- **AND** the suggestion SHALL NOT carry the note about unrestricted keys

#### Scenario: Other 400 from Gemini
- **WHEN** Gemini returns HTTP 400 with any other reason
- **THEN** the plugin SHALL NOT suggest checking the API key

#### Scenario: Gemini permission error
- **WHEN** Gemini returns HTTP 403 with `error.status` `PERMISSION_DENIED`, or HTTP 403 with no recognized error field
- **THEN** the suggestion SHALL say to check the API key in the Direct API settings
- **AND** it SHALL say that older keys not restricted to the Gemini API are rejected or blocked, and that a new key from Google AI Studio fixes it
- **AND** the provider's own message SHALL be kept

#### Scenario: Other providers' permission errors are unchanged
- **WHEN** Claude or OpenAI returns HTTP 403
- **THEN** the suggestion SHALL be to check the API key, with no note about Gemini keys

### Requirement: Error suggestions on every Direct API surface

Every surface that reports a Direct API failure SHALL show the suggestion with the provider's message: Settings Test, Generate, Generate All, Fast-Forward, Explore and Verify. Mapped messages that replace the provider's (the Claude workspace errors) and the OpenAI Organization/Project 401 suggestion SHALL keep their wording and precedence.

#### Scenario: Test shows the classified suggestion
- **WHEN** Test fails with a billing, quota, spend-limit, rate-limit or overload error
- **THEN** the Test result SHALL show that error's suggestion, not only the raw message

#### Scenario: Test keeps the key wording
- **WHEN** Test fails with a 401 or 403 and no OpenAI Organization or Project ID is configured
- **THEN** the Test result SHALL say to check the API key, as today

#### Scenario: Test explains a Gemini permission error
- **WHEN** Test fails because Gemini returned a permission error
- **THEN** the Test result SHALL say to check the API key and SHALL add the note about older unrestricted keys and creating a new key in Google AI Studio

#### Scenario: Explore shows the suggestion
- **WHEN** an Explore request over Direct API fails with an error that has a suggestion
- **THEN** the Explore panel and its notification SHALL show the suggestion with the message

#### Scenario: Verify shows the suggestion
- **WHEN** Verify's correctness check fails with an error that has a suggestion
- **THEN** the "check unavailable" finding SHALL include the suggestion

#### Scenario: Claude workspace message keeps precedence
- **WHEN** Claude returns one of the workspace-selection errors
- **THEN** the plugin SHALL show the mapped workspace message, as today, with the request ID added
