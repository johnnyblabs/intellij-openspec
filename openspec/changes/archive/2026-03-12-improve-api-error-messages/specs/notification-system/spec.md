## ADDED Requirements

### Requirement: Structured API error notifications

API error notifications SHALL display a human-readable error message with an actionable suggestion instead of raw HTTP response bodies. The `AiApiException` SHALL carry structured fields (HTTP status code, provider name, suggestion) so callers can format context-appropriate messages.

#### Scenario: Authentication error suggests checking API key
- **WHEN** the AI provider returns HTTP 401 or 403
- **THEN** the error notification SHALL include the suggestion "Check your API key in Settings → Tools → OpenSpec"
- **AND** the notification SHALL include an "Open Settings" action

#### Scenario: Rate limit error suggests waiting
- **WHEN** the AI provider returns HTTP 429
- **THEN** the error notification SHALL include the suggestion "Rate limited — wait a moment and retry"

#### Scenario: Server error suggests checking provider status
- **WHEN** the AI provider returns HTTP 500 or higher
- **THEN** the error notification SHALL include the suggestion "The provider may be experiencing issues — try again shortly"

#### Scenario: Error message extracted from provider JSON
- **WHEN** the AI provider returns an error with a JSON body containing an error message field
- **THEN** the notification SHALL display the extracted message instead of the raw JSON body

#### Scenario: Fallback for unparseable error responses
- **WHEN** the AI provider returns an error with a body that cannot be parsed as JSON
- **THEN** the notification SHALL display a truncated version of the raw body (max 200 characters)

#### Scenario: Full error details logged for debugging
- **WHEN** an API error occurs
- **THEN** the full HTTP status code and raw response body SHALL be logged at WARN level
