## 1. Per-provider list-models fetch (`DirectApiService`)

- [x] 1.1 Add `listModels(AiProvider, apiKey)` that issues the provider's list-models `GET` with the same auth headers as generation (OpenAI `Authorization: Bearer`; Anthropic `x-api-key` + `anthropic-version`; Gemini `x-goog-api-key`), on a background thread. Non-200 → `AiApiException` via the existing `buildApiError`.
- [x] 1.2 Extract a static parser per provider (`parseOpenAiModels`/`parseAnthropicModels`/`parseGeminiModels`) mirroring the existing response parsers, so they're contract-testable: OpenAI `data[].id`; Anthropic `data[].id`; Gemini `models[]` filtered to `supportedGenerationMethods` containing `generateContent`, with the `models/` prefix stripped from `name`. OpenAI id-prefix filter vs show-all per the design Open Question.

## 2. Test = verify-and-populate (`OpenSpecSettingsPanel`)

- [x] 2.1 In `testApiConnection`'s `SwingWorker.doInBackground`, replace the token-costing test-prompt with `listModels(...)`. On `200` → populate the editable `aiModelCombo` from the fetched list and show "Connected — N models"; on `401/403` → "Check your API key"; other → the provider message. Post the populate/status via `invokeLater(..., ModalityState)`.
- [x] 2.2 Preserve the user's current model selection if it's still present after populate; keep the combo editable.

## 3. Fallback

- [x] 3.1 On no key / offline / non-200 / parse failure, fall back to `AiProvider.getModels()` (the static list) — never leave the selector empty. Confirm `AiProvider`'s static lists are used only as the fallback in the model-selection path.

## 4. Contract tests + fixtures

- [x] 4.1 Capture a list-models example response per provider under `src/test/resources/fixtures/` (Gemini from a real free AI Studio key; OpenAI/Anthropic from their official docs' example `/v1/models` responses), sanitized of any secrets.
- [x] 4.2 Contract-test each list parser against its captured fixture — assert the parsed model ids, and that the Gemini filter drops non-`generateContent` models. Reconcile exact fixture/shape with the test-engineer PLAN.
- [x] 4.3 Unit-test fallback-on-failure (error/empty → static list) and that a typed-in model not in the list is preserved (editable override).

## 5. Documentation

- [x] 5.1 CHANGELOG `## Unreleased → Added`: Direct API now lists your account's real models (Test fetches them with your key; no token cost); falls back to a static list offline.
- [x] 5.2 Update `docs/feature-reference.md` (and marketplace page if it describes model selection) to reflect dynamic discovery. Verify `DocumentationHygieneTest` stays green.

## 6. Verify

- [x] 6.1 **test-engineer PLAN** at apply start — the list parsers are parsers of external output → captured-fixture contract-test discipline; set the per-provider fixture strategy (esp. OpenAI/Anthropic without keys).
- [ ] 6.2 `./gradlew build` green (test + JaCoCo floor).
- [ ] 6.3 Manual sandbox check: Gemini free key → Test → the Model dropdown populates with the account's live models; on a bad key, a clear error and the static fallback.
- [ ] 6.4 **intellij-code-reviewer** after implementation (touches the settings panel EDT path + `DirectApiService`).

## Note

This change **supersedes and retires** the deferred OpenAI-hardcoded-model-refresh item — OpenAI (and every provider's) models are now fetched live, so there is no hardcoded OpenAI list to verify.
