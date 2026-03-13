## Context

`DirectApiService` calls three AI providers (Claude, OpenAI, Gemini) via Java 21 HttpClient. Errors are thrown as `AiApiException(String)` with format: `"[Provider] API error (HTTP NNN): [raw body]"`. Callers catch this and show the message directly in notifications. The raw JSON body is unhelpful to users.

## Goals / Non-Goals

**Goals:**
- Structured `AiApiException` with status code, provider, suggestion fields
- Human-readable error messages with actionable advice per HTTP status category
- Extract provider-specific error message from JSON response body
- Log raw response bodies at WARN level for debugging

**Non-Goals:**
- Retry logic or automatic recovery
- Custom error UI beyond existing notification patterns
- Console tab output changes (notifications are the primary surface)

## Decisions

### 1. Add fields to AiApiException rather than creating subclasses

Add `statusCode`, `provider`, and `suggestion` fields directly to `AiApiException`. Callers access structured data via getters.

**Rationale**: Simple — one exception type, easy to construct, easy to inspect. Subclasses per error type would be over-engineered for 3 status categories.

### 2. Status-to-suggestion mapping in DirectApiService

A private helper method maps HTTP status code ranges to suggestions:
- 401/403 → "Check your API key in Settings → Tools → OpenSpec"
- 429 → "Rate limited — wait a moment and retry"
- 500-599 → "Provider is experiencing issues — check [provider] status page"
- Other 4xx → provider-specific error message from response body

**Rationale**: Keeps mapping logic close to the HTTP calls. The suggestions are static strings — no need for a separate class.

### 3. Parse provider error JSON for the message field

Each provider returns errors in slightly different JSON:
- Claude: `{"error": {"message": "..."}}`
- OpenAI: `{"error": {"message": "..."}}`
- Gemini: `{"error": {"message": "..."}}`

All three use the same nested path. Extract with a single helper. Fall back to truncated raw body if parsing fails.

**Rationale**: Users see "Invalid API key" instead of a 500-character JSON blob.

## Risks / Trade-offs

- **[Low] JSON parsing failure**: If a provider changes their error format, we fall back to truncated raw body. No worse than today.
- **[Low] Suggestion accuracy**: Generic suggestions (e.g., "check status page") may not match every 5xx scenario, but are still more helpful than a raw JSON dump.
