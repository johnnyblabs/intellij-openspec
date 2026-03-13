## Why

API error messages from `DirectApiService` dump raw HTTP status codes and full response bodies into notifications. A user seeing "Claude API error (HTTP 429): {"type":"error","error":{"type":"rate_limit_error"...}}" gets no actionable guidance. `AiApiException` carries only a string message with no structured fields, so callers can't distinguish a 401 from a 500 or offer targeted advice.

## What Changes

- Enhance `AiApiException` to carry structured error info: HTTP status code, provider name, and a user-friendly suggestion
- Add a private helper in `DirectApiService` that maps HTTP status codes to actionable error messages (401 → check API key, 429 → rate limited, 500+ → provider issue)
- Parse provider-specific error JSON to extract the error message field instead of dumping raw response bodies
- Log full error details (including raw body) at WARN level for debugging
- Update notification call sites to use the structured exception for cleaner user-facing messages

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `notification-system`: Add requirement for structured API error notifications with actionable suggestions

## Impact

- **Files modified**: `AiApiException.java`, `DirectApiService.java`, `WorkflowActionPanel.java` (error display)
- **Risk**: Low — error path changes only, no happy-path modifications
- **Dependencies**: None new
