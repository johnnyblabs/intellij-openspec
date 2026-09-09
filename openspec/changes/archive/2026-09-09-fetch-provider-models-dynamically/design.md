## Context

See proposal.md — Why. `DirectApiService` already holds each provider's auth headers and JSON parsing for generation; adding a symmetric `listModels(provider, key)` is a small addition. The settings Test path is already a background `SwingWorker`, so the fetch has an off-EDT home. This change makes `AiProvider`'s static lists a fallback rather than the source of truth, and retires the deferred OpenAI-hardcoded-refresh item.

## Goals / Non-Goals

**Goals:** the Model selector reflects the user's real, current, per-account models; Test validates the key cheaply (a `GET`, no token cost); graceful fallback so nothing is ever empty.

**Non-Goals:** persisting/caching model lists across sessions or background auto-refresh (fetch on demand); any change to the generation request/response contract; changing which provider is the default.

## Decisions

**1. One `listModels` per provider; parse the documented list shape.**
- **OpenAI** `GET /v1/models` → `{data:[{id}]}`. No per-model capability flag, so **filter by id prefix** (`gpt-`, `o`, `chatgpt-`) to drop embeddings/tts/whisper/image models, OR show all — decided at apply (Open Question). Editable selector covers the heuristic's misses.
- **Anthropic** `GET /v1/models` (`x-api-key` + `anthropic-version`) → `{data:[{id,display_name}]}`. All entries are Messages-compatible → use `id` directly.
- **Gemini** `GET /v1beta/models` (`x-goog-api-key`) → `{models:[{name:"models/…",supportedGenerationMethods:[…]}]}`. Filter `supportedGenerationMethods` containing `generateContent`; strip the `models/` prefix from `name`.

**2. Test = verify-and-populate (replaces the test-prompt).**
Clicking Test issues the `listModels` `GET`. `200` → success + populate the editable selector; `401/403` → "Check your API key"; other → provider message. This validates the key **without a token-costing generation** and fills the dropdown in one action. Keep the fetch in the existing `SwingWorker.doInBackground` (off-EDT); post the populate + status label via `invokeLater(..., ModalityState)`.
- *Alternative — keep the test-prompt and add a separate Refresh button:* rejected; the user asked for Test to do the discovery, and listing is strictly cheaper/safer key-validation.

**3. Fallback, never empty.** No key / offline / non-200 / parse failure → fall back to `AiProvider`'s static list (which stays a small, current default set governed by "AI providers"). The selector is always editable.

**4. Contract-test the list parsers against captured examples.** Per the `ai-integration` contract-test rule, drive each `listModels` parser from a **captured** provider list-models response under `src/test/resources/fixtures/…`. Gemini's can be captured from a real free AI Studio key; OpenAI's and Anthropic's from their official docs' example `/v1/models` responses (captured real shapes) until a key is available — sanitized, no secrets.

## Risks / Trade-offs

- **OpenAI has no capability flag** → a prefix heuristic may include a non-chat model or exclude a valid one. Mitigation: editable selector + a show-all fallback option; the heuristic is cosmetic, not correctness (a wrong pick just errors on use, recoverably).
- **Rate limits on the free tier** (Gemini `429` on list) → treated as a fetch failure → static fallback; surface the rate-limit suggestion.
- **Relationship to `harden-direct-api-flow`** → both touch the settings Test/credential path. This change's fetch must be off-EDT; if `harden-direct-api-flow` lands first, reuse its off-EDT credential plumbing; if this lands first, it does its own off-EDT in the existing `SwingWorker`. Independent but complementary — note in sequencing.
- **Retiring the static-list-as-source-of-truth** → the fallback list can still drift, but it only matters when discovery is unavailable, and it no longer causes a first-call 404 for online users with a key.

## Open Questions

- OpenAI chat-model filter: id-prefix allowlist vs. show-all — resolve at apply (default to show-all if the prefix set proves lossy).
- Whether to auto-populate on key entry (focus-lost) in addition to the explicit Test/Refresh — minor UX, decide at apply.

## Migration Plan

Additive — no migration. Existing users' saved `aiModel` is preserved; the selector simply gains live options. Rollback reverts `listModels` + the Test wiring; the static list remains functional.
