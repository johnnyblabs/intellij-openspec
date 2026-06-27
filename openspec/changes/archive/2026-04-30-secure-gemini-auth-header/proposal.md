## Why

A pre-release audit of `DirectApiService` flagged that the Gemini API key travels in the URL query string (`?key=<API_KEY>`) rather than the `x-goog-api-key` request header. Keys in URLs leak into HTTP access logs, IDE network inspectors, error-reporting tools, and any intermediary that records URLs — every other Direct API provider in the plugin already uses headers, so this is a hygiene gap, not a missing feature.

This change ships *only* the Gemini auth migration plus a small testability refactor — items that can be made and verified without depending on external provider documentation or guessed model IDs. A broader audit-driven change (`update-direct-api-providers`) was scoped, then narrowed after an engineering review found that several of its items required unverified facts (current model-ID strings, exact `anthropic-version` date, justified MAX_TOKENS values). Those defer to a later change; what survives here is the confident slice.

## What Changes

- **`callGemini` auth migration** — remove the `?key=<API_KEY>` URL query string; add `x-goog-api-key: <API_KEY>` request header instead. The endpoint URL becomes `${GEMINI_API_URL}${model}:generateContent` (no query string).
- **Extract a `buildGeminiRequest(...)` helper** that constructs the `HttpRequest` (URL, headers, body) without sending it. This makes the auth header assertable from unit tests without standing up a stub server.
- **Add a unit test** that calls `buildGeminiRequest(...)` and asserts: (a) the resulting URL contains no `?key=` substring, (b) the request has `x-goog-api-key` set to the key, and (c) the body shape (model URL path, contents/parts/text, generationConfig.maxOutputTokens) is preserved.
- **Add a unit test for `openAiTokenParam`** — pin the *current* routing behavior (`o1`-prefixed models → `max_completion_tokens`; everything else → `max_tokens`). This locks the contract so a future refactor that intends to extend the logic can't silently regress current behavior. No new prefixes are added; the test reflects only what the code does today.

Explicitly *not* in this change: model-list updates, `anthropic-version` changes, `MAX_TOKENS` adjustments, `openAiTokenParam` prefix expansion. Those deferred items were dropped because they require facts (current public model IDs, exact API version dates, validated token caps) that the engineering review concluded should not be guessed at.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: existing `AI providers` requirement gains one new scenario covering the Gemini auth-header contract. No other changes to the requirement.

## Impact

- Code: `src/main/java/com/johnnyblabs/openspec/ai/DirectApiService.java` — ~10-15 LOC delta (extract helper, change URL/header, no other touches).
- Tests: new test class or extension under `src/test/java/com/johnnyblabs/openspec/ai/` with two tests (Gemini request shape + openAi token-param pin).
- Settings UI: no change.
- Persisted state: no change.
- Risk: very low. Mechanical Gemini change; pure refactor on the OpenAI side. Existing user configurations remain valid.

### Out of scope

- **Model-list refresh** for Claude / OpenAI / Gemini. Requires verifying current public model IDs against provider docs; not done in this change. Tracked separately when verification is performed.
- **`openAiTokenParam` prefix expansion** for `o3` / `o4` / `gpt-5` reasoning models. Requires confirming those families exist as exact strings in the OpenAI API; not done here. The test added in this change pins the existing `o1`-only behavior so the future expansion change can be safely additive.
- **`anthropic-version` header bump.** Specific dated revisions (`2024-10-22`, `2024-11-05`, etc.) require docs verification; deferred.
- **`MAX_TOKENS` per-provider tuning.** Specific values require empirical justification; deferred.
- **Streaming, retry-on-429, prompt caching.** Always out of scope here; v0.3.0+ work.

## References

- Tracker: the linked issue
- Plane: openspec/issue/215 (`8a0c8bf3-dfb9-4a37-9971-2e4c8a814a5a`)
- Audit context: `update-direct-api-providers` (superseded; never archived) — the broader scope this change was narrowed from after engineering review.
