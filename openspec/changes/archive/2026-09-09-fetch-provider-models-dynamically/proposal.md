## Why

The plugin hardcodes a per-provider model list, which **drifts and goes stale**: Gemini's default recently 404'd on fresh free keys because the 2.5 line is retiring, and OpenAI's list can't be safely refreshed without a key (guessing IDs 401s). Every provider exposes a **list-models endpoint** callable with the user's own key, so the plugin can fetch the **real, current, per-account** model list instead of maintaining hardcoded lists. This eliminates model drift at the root, makes the Model selector always accurate for the user's account, and provides free key validation (a `GET`, no token cost). It **supersedes** the deferred OpenAI-hardcoded-refresh follow-up — there are no IDs to hardcode or verify.

## What Changes

- Add a per-provider **list-models fetch** using the configured key and the same auth headers already used for generation:
  - OpenAI `GET https://api.openai.com/v1/models` (`Authorization: Bearer`)
  - Anthropic `GET https://api.anthropic.com/v1/models` (`x-api-key` + `anthropic-version`)
  - Gemini `GET https://generativelanguage.googleapis.com/v1beta/models` (`x-goog-api-key`)
- The **Test action verifies the key by fetching the model list** (a free `GET`, no token cost) and populates the Model selector with the account's real models; on `401`/error it shows a provider-specific message. This replaces the token-costing test-prompt.
- **Filter to chat/generation-capable** models where the provider marks it (Gemini: `supportedGenerationMethods` contains `generateContent`); for OpenAI show all or an id-prefix heuristic (no capability flag exists); Anthropic lists only Messages-compatible models.
- **Fall back to a small static default list** when there is no key, the plugin is offline, or the fetch fails; keep the selector **editable** for manual override.
- The fetch runs **off the EDT** (network I/O), reusing the credential-off-EDT path.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `ai-integration`: MODIFY the "AI providers" requirement (the connection test now validates by listing models; the static default becomes a fallback) and ADD a "Dynamic model discovery" requirement (fetch → populate → filter → fallback → off-EDT → editable override, with the list-models parsers contract-tested against captured provider examples).

## Impact

- **Code:** `ai/DirectApiService` (add `listModels(provider, key)` per provider + a parser for each list shape), `settings/OpenSpecSettingsPanel` (Test → fetch + populate; off-EDT), `ai/AiProvider` (the static lists become a small offline fallback, not the source of truth).
- **Supersedes** the OpenAI-hardcoded-refresh follow-up (retired) — the OpenAI list is now fetched live.
- **Tests:** contract-test each provider's list-models parser against a captured example response (the `ai-integration` contract-test rule); unit-test the fallback-on-failure and the chat-capability filter.
- **No change** to the generation request/response contract, auth headers, or response parsing.
- **Platform:** the fetch is a network call and MUST run off the EDT (pairs with the `harden-direct-api-flow` credential-off-EDT work). No IntelliJ 2024.2+ compatibility change.
