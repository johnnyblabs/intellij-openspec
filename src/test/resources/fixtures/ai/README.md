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

## Gemini generateContent — live captures

Captured 2026-10-25 with `scripts/capture-gemini-generatecontent.py`. It sends the plugin's own request (`DirectApiService.buildGeminiRequest`) with a free AI Studio key read from `$GEMINI_API_KEY`, and refuses to save a response that contains the key. They back the live Gemini tests in `DirectApiResponseContractTest`.

| File | Model | What it shows |
|------|-------|---------------|
| `gemini/live/generatecontent-text.json` | `gemini-3.8-flash`, the plugin's default, at the plugin's `maxOutputTokens` 16000 | `finishReason: STOP`. The reply's **only** part carries both `text` and a `thoughtSignature`. No thought part is returned, because the plugin doesn't ask for one (`includeThoughts`), but `usageMetadata.thoughtsTokenCount` shows the model thought. |
| `gemini/live/generatecontent-max-tokens.json` | `gemini-3.6-flash`, with `maxOutputTokens` 16 | `finishReason: MAX_TOKENS` with `content: {}`, an object with **no `parts`**. Thinking used the whole budget before any text. |

The token-limit case is from `gemini-3.6-flash` because `gemini-3.8-flash` answered `503 UNAVAILABLE` ("This model is currently experiencing high demand…") on all four attempts that day. The script retries a 503 or a timeout up to four times. A blocked-prompt response isn't captured, since there's no benign way to trigger one, so that case stays derived (below).

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
