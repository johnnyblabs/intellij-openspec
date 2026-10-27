## ADDED Requirements

### Requirement: Direct API error classification

When a Direct API request fails, the plugin SHALL choose its suggestion from the provider's documented error field in the body (Claude `error.type` and `error.details.error_code`, OpenAI `error.code`, `error.type` and `error.param`, Gemini `error.status` and `details[].reason`), and SHALL fall back to the HTTP status only when the body carries no recognized field. The provider's own error message SHALL be kept. This applies to Test and every generation path.

#### Scenario: Body field decides over status
- **WHEN** a provider returns HTTP 429 with a body whose code names a quota or spend limit
- **THEN** the plugin SHALL give the quota or spend-limit suggestion, not the rate-limit one

#### Scenario: Body that isn't JSON
- **WHEN** a provider returns an error whose body is not JSON or has no recognized field
- **THEN** the plugin SHALL choose the suggestion from the HTTP status, and SHALL show the start of the body as the message, as today

#### Scenario: JSON body sent as plain text
- **WHEN** OpenAI returns an error with a JSON body and a `text/plain` content type
- **THEN** the plugin SHALL still read the body's error fields

### Requirement: Billing, quota and spend-limit errors are not called retryable

A billing, payment, quota or spend-limit error SHALL get a suggestion that points at the provider's billing or limits settings, and SHALL NOT advise waiting and retrying.

#### Scenario: OpenAI quota or spend code
- **WHEN** OpenAI returns HTTP 429 with `error.code` `credit_balance_exhausted`, `organization_spend_limit_exceeded`, `project_spend_limit_exceeded` or `organization_usage_limit_exceeded`, or with `error.type` `insufficient_quota`
- **THEN** the suggestion SHALL point at OpenAI's billing or limits settings and SHALL NOT advise retrying

#### Scenario: Claude billing error
- **WHEN** Claude returns HTTP 402 with `error.type` `billing_error`
- **THEN** the suggestion SHALL point at the Claude Console's billing settings

#### Scenario: Claude spend cap reached
- **WHEN** Claude returns HTTP 429 with `error.details.error_code` `enforced_spend_limit_reached`
- **THEN** the suggestion SHALL say the organization's spend cap is reached and SHALL NOT advise retrying

#### Scenario: Claude user-set usage limit reached
- **WHEN** Claude returns HTTP 400 with `error.type` `invalid_request_error` and a message that starts with "You have reached your specified"
- **THEN** the suggestion SHALL point at the usage limits in the Claude Console

#### Scenario: Gemini payment required
- **WHEN** Gemini returns HTTP 402
- **THEN** the suggestion SHALL point at the Gemini API billing settings and SHALL NOT advise retrying

### Requirement: Rate-limit and overload errors

A plain rate-limit error, and an overloaded or unavailable provider, SHALL get a suggestion to wait and try again. The plugin SHALL NOT retry the request automatically.

#### Scenario: Plain rate limit
- **WHEN** a provider returns HTTP 429 whose body names no quota, billing or spend-limit cause
- **THEN** the suggestion SHALL say the request was rate limited and to wait and try again

#### Scenario: Provider overloaded
- **WHEN** Claude returns HTTP 529, or a provider returns HTTP 503
- **THEN** the suggestion SHALL say the provider is overloaded or unavailable and to try again shortly

### Requirement: Key and permission errors per provider

A rejected API key SHALL get the "check your API key" suggestion whatever status the provider uses for it. Gemini rejects an invalid key with HTTP 400 and reason `API_KEY_INVALID`.

#### Scenario: Gemini invalid key
- **WHEN** Gemini returns HTTP 400 with a `details[].reason` of `API_KEY_INVALID`
- **THEN** the suggestion SHALL be to check the API key in the Direct API settings

#### Scenario: Other 400 from Gemini
- **WHEN** Gemini returns HTTP 400 with any other reason
- **THEN** the plugin SHALL NOT suggest checking the API key

### Requirement: OpenAI service tier not allowed

When OpenAI rejects a request with HTTP 400 and `error.param` `service_tier`, the plugin SHALL keep OpenAI's message and SHALL suggest checking which service tiers the OpenAI project allows. The plugin names no service tier, so the request resolved to the project's default.

#### Scenario: Disallowed service tier
- **WHEN** OpenAI returns HTTP 400 with `error.type` `invalid_request_error` and `error.param` `service_tier`
- **THEN** the suggestion SHALL point at the OpenAI project's allowed service tiers

### Requirement: Provider request ID in errors

When a failed response carries a provider request ID (Claude's `request-id` header or body `request_id`, OpenAI's `x-request-id` header), the error SHALL show it, labelled as the request ID to quote to the provider's support, and the plugin SHALL log it. An absent ID SHALL add nothing.

#### Scenario: Claude error with request ID
- **WHEN** Claude returns an error with a `request-id` header
- **THEN** the error SHALL show that request ID

#### Scenario: Claude body only
- **WHEN** Claude returns an error with no `request-id` header and a body `request_id`
- **THEN** the error SHALL show the body's request ID

#### Scenario: OpenAI error with request ID
- **WHEN** OpenAI returns an error with an `x-request-id` header
- **THEN** the error SHALL show that request ID

#### Scenario: No request ID
- **WHEN** a provider returns an error with no request ID, as Gemini does
- **THEN** the error SHALL show no request-ID text

### Requirement: Error suggestions on every Direct API surface

Every surface that reports a Direct API failure SHALL show the suggestion with the provider's message: Settings Test, Generate, Generate All, Fast-Forward, Explore and Verify. Mapped messages that replace the provider's (the Claude workspace errors) and the OpenAI Organization/Project 401 suggestion SHALL keep their wording and precedence.

#### Scenario: Test shows the classified suggestion
- **WHEN** Test fails with a billing, quota, spend-limit, rate-limit or overload error
- **THEN** the Test result SHALL show that error's suggestion, not only the raw message

#### Scenario: Test keeps the key wording
- **WHEN** Test fails with a 401 or 403 and no OpenAI Organization or Project ID is configured
- **THEN** the Test result SHALL say to check the API key, as today

#### Scenario: Explore shows the suggestion
- **WHEN** an Explore request over Direct API fails with an error that has a suggestion
- **THEN** the Explore panel and its notification SHALL show the suggestion with the message

#### Scenario: Verify shows the suggestion
- **WHEN** Verify's correctness check fails with an error that has a suggestion
- **THEN** the "check unavailable" finding SHALL include the suggestion

#### Scenario: Claude workspace message keeps precedence
- **WHEN** Claude returns one of the workspace-selection errors
- **THEN** the plugin SHALL show the mapped workspace message, as today, with the request ID added
