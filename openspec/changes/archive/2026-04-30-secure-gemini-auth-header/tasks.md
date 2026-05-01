## 1. Gemini auth header migration

- [x] 1.1 Extract a package-private `static HttpRequest buildGeminiRequest(String model, String apiKey, String prompt)` method in `DirectApiService`. It builds the same body shape as today (contents/parts/text + generationConfig.maxOutputTokens) but produces a `HttpRequest` whose URI is `${GEMINI_API_URL}${model}:generateContent` (no query string) and whose headers include `Content-Type: application/json` and `x-goog-api-key: ${apiKey}`.
- [x] 1.2 Change `callGemini` to call `buildGeminiRequest(...)` and send the resulting request. All other behavior (response parsing, error handling, timeout) stays unchanged.

## 2. Tests

- [x] 2.1 `BuildGeminiRequest$setsXGoogApiKeyHeader` asserts `request.headers().firstValue("x-goog-api-key").orElse(null)` equals the supplied key.
- [x] 2.2 `BuildGeminiRequest$doesNotEmbedKeyInUrl` asserts the URI contains neither `?key=` nor the literal API key value.
- [x] 2.3 `BuildGeminiRequest$preservesEndpointPath` asserts the URI ends with `/${model}:generateContent`. Plus two bonus assertions: `setsContentTypeJson` and `usesPostMethod` verify the rest of the request shape didn't drift.
- [x] 2.4 The existing `OpenAiTokenParam` nested class in `DirectApiServiceTest` already pins the current routing comprehensively — `gpt4o_usesMaxTokens`, `gpt4oMini_usesMaxTokens`, `o1Mini_usesMaxCompletionTokens`, `o1Preview_usesMaxCompletionTokens`, `o1_usesMaxCompletionTokens`, `gpt35_usesMaxTokens`. Pre-existing coverage; no new tests needed for this task.

## 3. Verify

- [x] 3.1 Run `./gradlew test` and confirm the suite is green; new tests pass. (739 tests / 0 failures, up from 734 — accounts for 5 new tests in the `BuildGeminiRequest` nested class.)
- [x] 3.2 Run `./gradlew compileJava` to confirm the helper extraction compiles cleanly. Compile clean.
- [x] 3.3 Manual smoke (deferred to release-prep validation): with a valid Gemini API key configured in Settings, click "Test Connection" and confirm a successful round-trip (proving the header-based auth path works against the real API). Same step for an actual Generate cycle. **Deferred** — same pattern as prior changes; unit tests cover the request-shape invariants exhaustively.
