## 1. Enhance AiApiException

- [x] 1.1 Add `statusCode`, `provider`, and `suggestion` fields with getters to `AiApiException`
- [x] 1.2 Add a convenience constructor that accepts all structured fields

## 2. Improve DirectApiService error handling

- [x] 2.1 Add private helper `extractErrorMessage(String responseBody)` that parses JSON error message, falling back to truncated body
- [x] 2.2 Add private helper `suggestionForStatus(int statusCode)` that maps status codes to actionable suggestions
- [x] 2.3 Update `callClaude()`, `callOpenAi()`, `callGemini()` to use structured `AiApiException` with parsed message, status code, provider, and suggestion
- [x] 2.4 Log full error details (status code + raw body) at WARN level before throwing

## 3. Update notification call sites

- [x] 3.1 Update `WorkflowActionPanel` error handler to display suggestion from `AiApiException` when available

## 4. Verify

- [x] 4.1 Run full test suite to confirm no regressions
