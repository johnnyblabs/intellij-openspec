# AI provider response fixtures — provenance

The OpenAI and Gemini JSON files below are the **providers' own published example response shapes** (except the live Gemini captures under `gemini/live/`, see below),
transcribed from each provider's public API reference. The Claude files are **live captures** (see
"Claude fixtures — live-captured" below). They back the parser contract tests in
`DirectApiResponseContractTest` so the response parsers in `ai/DirectApiService.java` are verified
against the providers' *stated contract* rather than a shape we assume.

**Why doc-sourced, not tool-captured (important — read before editing):** the repo's usual
contract-test discipline captures **real** output from the live tool (see `fixtures/cli/README.md`).
That is impossible here: exercising the live Anthropic / OpenAI / Gemini APIs requires paid API
keys, which the maintainer does not hold. A provider's *documented* example response is the
strongest available substitute — it is the provider's own stated contract, categorically better
than a hand-authored shape (which would merely encode our assumption, the exact failure mode this
test class exists to prevent). It can nonetheless lag the live API.

**When API credentials become available:** re-capture a live response per provider (isolate the key
via an env var, never commit it), diff it against these files, and fix any parser mismatch by
updating the parser — treat the committed fixture as the contract of record until then.

| File | Provider | Documented shape source | Key path the parser reads |
|------|----------|-------------------------|---------------------------|
| `openai/chat-completion-response.json` | OpenAI | Chat Completions reference — "The chat completion object" example (`platform.openai.com/docs/api-reference/chat/object`) | `choices[0].message.content` |
| `gemini/generatecontent-response.json` | Google Gemini | `generateContent` reference — response example (`ai.google.dev/api/generate-content`) | the text of every `candidates[0].content.parts[]` entry not marked `thought`, concatenated |
| `openai/chat-completion-openapi-example.json` | OpenAI | `openapi.yaml` in OpenAI's `openai-openapi` repository — the Chat Completions "Default" example response, which carries `"refusal": null` | `choices[0].message.content`, then `refusal` and `finish_reason` |

Transcribed 2026-08-02 for change `fix-direct-api-provider-compliance`; the OpenAPI example was added 2026-10-22, because the reference page is not reachable by automated fetch.

Structural fidelity (field
nesting and the read paths above) is what the contract tests assert; the exact text strings are read
from these files, so refreshing a fixture to a newer documented example is safe as long as the shape
is preserved.

**Derived variants (no official example exists).** Neither provider publishes an example of an empty reply. The OpenAI null-content, refusal and token-limit cases, and the Gemini thought-part, missing-content and blocked-prompt cases, are built inside `DirectApiResponseContractTest` from the documented fixtures above. Each one changes only fields the provider's reference defines (OpenAI's nullable `content` and `refusal`, the `finish_reason` enum; Gemini's `Part.thought`, `FinishReason` and `promptFeedback.blockReason`). Replace them with live captures when keys are available.


## `gemini/store/` — where the `store` field goes

Captured live on 2026-10-27 from a standard `AIza…` AI Studio key, `v1beta` `:generateContent`, one request per file. Each case is the request body sent (`.request.json`), the raw response (`.response.json`) and the HTTP status (`.status`). The key was read from the environment and never written. Google's logs page documents `store` only in its SDKs, with no REST example, so these captures are the evidence for the REST placement.

| Case | Model | Result |
|---|---|---|
| `accepted-top-level-store` | `gemini-3.8-flash` | **200**, a normal reply. Top-level `"store": false` is accepted (it needed one retry after a `503` overload). |
| `rejected-generationconfig-store` | `gemini-3.8-flash` | **400** `INVALID_ARGUMENT`: `Unknown name "store" at 'generation_config'`. |

Not kept as files, but run the same day:
- top-level `"store": false` also got 200 on `gemini-3.6-flash`;
- an unknown top-level field (`notAField`) got 400 `Unknown name "notAField"`. That shows Google validates top-level names, so the 200 means `store` is recognised, not ignored.

Auth keys (`AQ.…`) were not probed.
## Gemini generateContent — live captures

Re-captured 2026-10-27 with `scripts/capture-gemini-generatecontent.py`, after the plugin started sending `"store": false`. The first capture was 2026-10-25. The script:
- sends the plugin's own request (`DirectApiService.buildGeminiRequest`) with a free AI Studio key read from `$GEMINI_API_KEY`;
- refuses to save a response that contains the key;
- records the request body it sent (`<case>.request.json`) and the HTTP status (`<case>.status`) next to each response.

`DirectApiResponseContractTest` checks that each recorded request equals the body the plugin builds, apart from the token budget, so the script and the plugin can't drift apart. The responses back the live Gemini tests in the same class.

| File | Model | What it shows |
|------|-------|---------------|
| `gemini/live/generatecontent-text.json` | `gemini-3.6-flash`, at the plugin's `maxOutputTokens` 16000 | `finishReason: STOP`. The reply's **only** part carries both `text` and a `thoughtSignature`. No thought part is returned, because the plugin doesn't ask for one (`includeThoughts`), but `usageMetadata.thoughtsTokenCount` shows the model thought. |
| `gemini/live/generatecontent-max-tokens.json` | `gemini-3.6-flash`, with `maxOutputTokens` 16 | `finishReason: MAX_TOKENS` with `content: {}`, an object with **no `parts`**. Thinking used the whole budget before any text. |

Both cases are from `gemini-3.6-flash` because `gemini-3.8-flash`, the plugin's default, answered `503 UNAVAILABLE` ("This model is currently experiencing high demand…") on every attempt: on 2026-10-25 for the token-limit case, and on 2026-10-27 for both. On 2026-10-27, `gemini-3.8-flash` did accept the same top-level `store` field in the `gemini/store/` probe. The script retries a 503 or a timeout up to four times. A blocked-prompt response isn't captured, since there's no benign way to trigger one, so that case stays derived (below).

These are the live contract for the cases they cover. The documented `generatecontent-response.json` and the variants derived from it still cover thought parts, missing content and blocked prompts.

## List-models fixtures — provenance

These back the list-models parser contract tests in `DirectApiModelsContractTest` (change
`fetch-provider-models-dynamically`). The Gemini one is a **live capture** (a free Google AI
Studio key), and so is the Anthropic one (see the Claude section below). OpenAI remains doc-sourced
(no paid key); re-capture it from a live key when one is available, diff, and fix the parser on any
mismatch.

| File | Provider | Provenance | Key path the parser reads |
|------|----------|-----------|---------------------------|
| `openai/models-list.json` | OpenAI | **DOCS** — models/list example (`platform.openai.com/docs/api-reference/models/list`); the page is 403 to automated fetch, so the example was corroborated field-for-field via web search 2026-09-09 | `data[].id` (show-all — see note) |
| `gemini/models-list.json` | Google Gemini | **LIVE** — captured from a free AI Studio key via `GET /v1beta/models?pageSize=1000` on 2026-09-09; `nextPageToken` dropped, trimmed to a representative set that **keeps at least one non-`generateContent` (embedding) entry** | `models[]` filtered to `supportedGenerationMethods` ∋ `generateContent`, `models/` prefix stripped |

**Gemini is the first *live* fixture in this dir** — do not "re-capture it away" to a documented
example; it is the stronger artifact. The embedding entry is **load-bearing**: it is what exercises
the `generateContent` filter's negative branch. Do not remove it when refreshing.

**OpenAI filter deferred (show-all):** `parseOpenAiModels` returns every `data[].id` — OpenAI's list
has no per-model chat-capability flag, and the only OpenAI fixture available is the verbatim docs
example, which contains no non-chat models to honestly test a filter against. Injecting a synthetic
embedding id into a docs fixture to test a drop would degrade it to a hand-authored shape (the exact
anti-pattern this dir exists to prevent). A prefix/capability filter is revisited only once a live
OpenAI key yields a real fixture containing non-chat models. The editable selector covers the noise
in the meantime. (Gemini keeps its filter precisely because its live fixture *does* carry the
non-`generateContent` entry.)

## Claude fixtures — live-captured

All Claude fixtures were captured from the live API on 2026-10-22 with a key that is **not scoped to
a workspace** (sent with `Authorization: Bearer` and, where noted, the `anthropic-workspace-id`
header). They replaced the earlier doc-sourced `messages-response.json` and `models-list.json`, which
predated two live behaviors the parser must handle: keys that need a workspace header, and models that
think by default.

**Sanitization applied:** `id` / `request_id` → `msg_SANITIZED` / `req_SANITIZED`, thinking
`signature` → `SIGNATURE_SANITIZED`; no key or workspace ID appears in any file. Before committing a
re-capture, `grep -rnE "sk-ant|wrkspc_[A-Za-z0-9]{6}" claude/` must return nothing.

| File | Request | Shape | Key path the code reads | Test |
|------|---------|-------|-------------------------|------|
| `claude/messages-text-only.json` | `POST /v1/messages`, `claude-sonnet-5` | `content: [text]` | every `content[].type == "text"` block's `text`, concatenated | `DirectApiResponseContractTest` |
| `claude/messages-thinking-first.json` | `POST /v1/messages`, `claude-sonnet-5-5` (thinking on by default) | `content: [thinking (empty text, signature), text]` | same; the `thinking` block is skipped | `DirectApiResponseContractTest` |
| `claude/messages-specs-multi-file.json` | `POST /v1/messages`, `claude-sonnet-5-5`, with the plugin's real Direct API `specs` prompt for the lifecycle demo change (saved beside it as `messages-specs-multi-file.prompt.md`, demo path replaced with `/demo`) | `content: [text]`, the text is `<!-- file: specs/greeting/spec.md -->` followed by the delta | the text, then `MultiFileArtifact.parse` into files | `DirectApiResponseContractTest` |
| `claude/models-list-live.json` | `GET /v1/models` with the workspace header | `data[]` of 14 models, `has_more: false` | `data[].id` (never `display_name`) | `DirectApiModelsContractTest` |
| `claude/models-400-unscoped-key.json` | `GET /v1/models` **without** the workspace header | HTTP 400, `error.type == "invalid_request_error"`, message names `anthropic-workspace-id` | `error.type` + `error.message` (mapped to an actionable message) | `DirectApiServiceTest` |
| `claude/models-400-invalid-workspace-id.json` | `GET /v1/models` with a well-formed but unknown `wrkspc_` ID | HTTP 400, `invalid_request_error`, message "…must be a valid workspace ID." (also names the header) | same; must **not** map to the unscoped-key message | `DirectApiServiceTest` |
| `claude/messages-400-missing-max-tokens.json` | `POST /v1/messages` without `max_tokens` | HTTP 400, `invalid_request_error`, no workspace involved | same; the provider's message is kept (negative control) | `DirectApiServiceTest` |

**No live 404.** The workspace-not-found mapping is tested on a body derived from the live 400 envelope: a 404
needs a well-formed ID for a workspace the key can't see, and every ID tried so far returned the format 400 above.
Capture one when such an ID is available.

**The thinking-first shape is load-bearing.** Models with thinking on by default (Claude 5.5 and
later) return a `thinking` block whose `thinking` text is empty (`display: "omitted"`) **before** the
`text` block. A parser that reads `content[0].text` fails on it. Do not trim the `thinking` block when
refreshing this fixture.

## OpenAI errors — live-captured without a key

An invalid-key 401 needs no paid key, so these are **live** captures, an exception to the doc-sourced OpenAI files above. Captured 2026-10-27 with the fake key `sk-fixture-invalid-key` (OpenAI echoes it masked; nothing to sanitize). They back the OpenAI 401 tests in `DirectApiServiceTest`.

| File | Request | What it shows |
|------|---------|---------------|
| `openai/models-401-invalid-key.json` | `GET /v1/models` with `OpenAI-Organization: org-fixture` and `OpenAI-Project: proj_fixture` | HTTP 401, `error.code: invalid_api_key`, `error.type: invalid_request_error` |
| `openai/chat-completions-401-invalid-key.json` | `POST /v1/chat/completions` with `OpenAI-Project: proj_fixture` | HTTP 401, the same `error` object in a different key order, plus a top-level `"status": 401` |

The two endpoints use different envelopes, so the tests read both. The organization and project headers did not change an invalid-key body, and OpenAI accepted both headers without a 400. A key that is valid but doesn't belong to the named organization or project needs a real key to capture, so the plugin's 401 hint for configured IDs is chosen on the status alone and parses nothing beyond `error.message`.

## Error responses — live, docs and derived

These back `DirectApiErrorsContractTest`, which classifies each body and reads each request ID. Every file under `<provider>/errors/` is a body (`.json`) plus the response headers that matter (`.headers`: `content-type`, the request-ID header, and `retry-after` when sent, one `name: value` per line). `DirectApiErrorsContractTest` fails if a file there isn't listed in this table.

**LIVE** captures used fake keys or no key on 2026-10-27, so nothing needed sanitizing; the request IDs are the real ones the providers returned. Billing, quota, spend-limit and overload errors can't be produced on demand. **DOCS** is a provider's verbatim example. **DERIVED** is a live envelope from this table with only the documented fields changed: the type, code, param or status the docs name, and a message taken from the docs' description of that error. A derived message that the docs don't give verbatim is marked *placeholder*, and no test reads it. Docs checked 2026-10-27: Claude `platform.claude.com/docs/en/api/errors` and `/rate-limits`; OpenAI `developers.openai.com/api/docs/guides/error-codes`; Gemini `ai.google.dev/gemini-api/docs/troubleshooting`, `/rate-limits` and `/billing` (Gemini's `api-errors` page documents the Interactions API, a different shape, and is not used).

| File | Provenance | What it shows |
|------|------------|---------------|
| `claude/errors/models-401-invalid-key` | LIVE (`GET /v1/models`) | `authentication_error`; `request-id` header equals body `request_id` |
| `claude/errors/messages-401-invalid-key` | LIVE (`POST /v1/messages`) | same, from generation |
| `claude/errors/messages-401-header-and-body-ids` | DERIVED (live 401 with the IDs changed) | header `request-id` and body `request_id` differ, so the test can tell which one wins |
| `claude/errors/messages-429-spend-cap` | DOCS (rate-limits, "Reaching your spend cap") | `rate_limit_error` with `details.error_code: enforced_spend_limit_reached`, no `retry-after` |
| `claude/errors/messages-402-billing-error` | DERIVED (errors page, 402 row) | `billing_error` |
| `claude/errors/messages-403-permission-error` | DERIVED (errors page, 403 row; message is that row's description) | `permission_error`, so a non-Gemini permission error is pinned by its body, not only by the status |
| `claude/errors/messages-400-api-usage-limit` | DERIVED (errors page, spend limits) | `invalid_request_error`, message starts "You have reached your specified API usage limits" |
| `claude/errors/messages-400-workspace-usage-limit` | DERIVED (same) | the workspace-limit prefix |
| `claude/errors/messages-529-overloaded` | DERIVED (errors page, 529 row) | `overloaded_error` |
| `claude/errors/messages-429-rate-limit` | DERIVED, message *placeholder* | plain `rate_limit_error` with `retry-after` |
| `openai/errors/models-401-invalid-key` | LIVE (`GET /v1/models`) | `x-request-id` header; no ID in the body |
| `openai/errors/chat-completions-401-invalid-key` | LIVE (`POST /v1/chat/completions`) | JSON body sent as `content-type: text/plain` |
| `openai/errors/429-credit-balance-exhausted` | DERIVED (error-codes table) | `code: credit_balance_exhausted`, `type: insufficient_quota` (the docs say the type "can still be" that) |
| `openai/errors/429-organization-spend-limit-exceeded` | DERIVED (error-codes, spend-limits) | `code: organization_spend_limit_exceeded` |
| `openai/errors/429-project-spend-limit-exceeded` | DERIVED (error-codes table) | `code: project_spend_limit_exceeded` |
| `openai/errors/429-organization-usage-limit-exceeded` | DERIVED (error-codes table) | `code: organization_usage_limit_exceeded` |
| `openai/errors/429-insufficient-quota` | DERIVED, message *placeholder* | `type: insufficient_quota`, `code: null` (the type fallback) |
| `openai/errors/429-slow-down` | DERIVED (error-codes table) | `type: rate_limit_error`, `code: slow_down`, a plain rate limit |
| `openai/errors/503-server-is-overloaded` | DERIVED (error-codes table) | `type: service_unavailable_error`, `code: server_is_overloaded` |
| `openai/errors/400-service-tier-not-allowed` | DERIVED, message verbatim from the error-codes page | `invalid_request_error`, `param: service_tier` |
| `gemini/errors/models-400-api-key-invalid` | LIVE (`GET models`) | **400**, not 401, for a bad key: `INVALID_ARGUMENT`, `details[].reason: API_KEY_INVALID`; no request-ID header |
| `gemini/errors/generatecontent-400-api-key-invalid` | LIVE (`:generateContent`) | same, from generation |
| `gemini/errors/models-403-no-key` | LIVE (no key) | `PERMISSION_DENIED` |
| `gemini/errors/generatecontent-403-no-key` | LIVE (no key) | same, from generation |

**No capture exists for a Gemini key that is blocked or not restricted to the Gemini API.** Google's API-key page says such keys are rejected, and that dormant ones are blocked, but documents no status, reason or message. A live probe on 2026-10-27 couldn't produce one: a standard key created before late May 2026 kept returning `200` for `generateContent` and `models` for 9 minutes after its API restriction was removed. So the key-restriction note is attached to every Gemini `PERMISSION_DENIED` (or bare `403`), pinned by the two `403-no-key` captures above. Both kinds of new key (AI Studio and Cloud console auth keys, `AQ.…`) returned `200` in the same probe with `x-goog-api-key` on `v1beta`. When a blocked key's real error is captured, add it here with its own rule.
| `gemini/errors/generatecontent-400-invalid-argument` | DERIVED, message *placeholder* (live 400 without the `API_KEY_INVALID` detail) | a 400 that is **not** a key error (negative control) |
| `gemini/errors/generatecontent-402-payment-required` | DERIVED (billing page) | HTTP 402; the RPC `status` string is undocumented, so the body has none and the HTTP status decides |
| `gemini/errors/generatecontent-429-resource-exhausted` | DERIVED, message *placeholder* (rate-limits page) | `RESOURCE_EXHAUSTED`, which Google documents as wait-and-retry even when spend-based |
| `gemini/errors/generatecontent-503-unavailable` | DERIVED, message from the live 503 noted above | `UNAVAILABLE` |

Re-capture the LIVE rows with a fake key whenever a provider's error envelope changes; replace a DERIVED row with a live capture whenever one becomes possible.
