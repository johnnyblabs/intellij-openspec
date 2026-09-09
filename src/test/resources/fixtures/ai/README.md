# AI provider response fixtures — provenance

These JSON files are the **providers' own published example response shapes**, transcribed from
each provider's public API reference. They back the parser contract tests in
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
| `claude/messages-response.json` | Anthropic | Messages API reference — response object example (`platform.claude.com/docs`, API → Messages) | `content[0].text` |
| `openai/chat-completion-response.json` | OpenAI | Chat Completions reference — "The chat completion object" example (`platform.openai.com/docs/api-reference/chat/object`) | `choices[0].message.content` |
| `gemini/generatecontent-response.json` | Google Gemini | `generateContent` reference — response example (`ai.google.dev/api/generate-content`) | `candidates[0].content.parts[0].text` |

Transcribed 2026-08-02 for change `fix-direct-api-provider-compliance`. Structural fidelity (field
nesting and the read paths above) is what the contract tests assert; the exact text strings are read
from these files, so refreshing a fixture to a newer documented example is safe as long as the shape
is preserved.

## List-models fixtures — provenance

These back the list-models parser contract tests in `DirectApiModelsContractTest` (change
`fetch-provider-models-dynamically`). Unlike the response fixtures above, the Gemini one is a **live
capture** — the maintainer holds a free Google AI Studio key, so its list-models `GET` is exercisable
for real. OpenAI and Anthropic remain doc-sourced (no paid keys); re-capture them from a live key when
one is available, diff, and fix the parser on any mismatch.

| File | Provider | Provenance | Key path the parser reads |
|------|----------|-----------|---------------------------|
| `openai/models-list.json` | OpenAI | **DOCS** — models/list example (`platform.openai.com/docs/api-reference/models/list`); the page is 403 to automated fetch, so the example was corroborated field-for-field via web search 2026-09-09 | `data[].id` (show-all — see note) |
| `claude/models-list.json` | Anthropic | **DOCS** — List Models 200 example (`platform.claude.com/docs/en/api/models-list`), fetched verbatim 2026-09-09 (single `claude-opus-5` entry with the full `capabilities` object) | `data[].id` (never `display_name`) |
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
