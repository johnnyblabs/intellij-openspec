## Context

`DirectApiErrors.classify` picks an `ErrorKind` from each provider's documented error field, falling back to the HTTP status, and `suggestion` words the hint. A 404 maps to `UNKNOWN`, which has no suggestion, so a retired saved model fails with only the provider's message. `DirectApiService.buildApiError` already replaces the Claude workspace-not-found 404 (`not_found_error`, message starting "Workspace") with its own message and clears the suggestion.

Real responses (captured 2026-11-22 with `scripts/capture-model-not-found.py`, the plugin's own request, a retired id and a never-existing id; stored in the contract test's naming, with the status in the file name and a `.headers` file):

| Provider | Status | Field | Message |
|---|---|---|---|
| OpenAI | 404 | `error.code` `model_not_found`, `error.type` `invalid_request_error` | "The model `<id>` does not exist or you do not have access to it." |
| Gemini | 404 | `error.status` `NOT_FOUND`, `error.code` 404 | "models/<id> is not found for API version v1beta, or is not supported for generateContent. …" |

For each provider the retired and never-existing responses differ only in the model id. No Anthropic key was available, so Claude has no live 404 capture.

## Goals / Non-Goals

**Goals:**
- Give a missing or retired model an actionable suggestion for all three providers.
- Keep the Claude workspace-not-found message and its precedence.

**Non-Goals:**
- Telling a retired model from one that never existed or one the key can't use. The providers don't distinguish them.
- Migrating saved selections automatically. Choosing the replacement stays the user's call.
- Notices for models that are about to retire. A separate tracker entry covers Gemini's scheduled routing notice.

## Decisions

- **One new kind, `MODEL_NOT_FOUND`, decided by the body field.** OpenAI's `model_not_found` code names the case on its own, so it decides whatever the status, as the existing classification rule says the body field decides over the status. OpenAI also sends that code when a project can't use a model, where the key hint would be wrong. Gemini's `NOT_FOUND` and Claude's `not_found_error` are generic 404 types, so they also need status 404. Gemini also needs a message naming `models/`, as both live captures do, so a 404 for another resource isn't called a missing model.
- **Claude workspace first, decided once.** `DirectApiErrors.isClaudeWorkspaceNotFound` (404, `not_found_error`, message starting "Workspace") is the single test. Classification uses it to exclude the case, and `DirectApiService.claudeWorkspaceMessage` uses it to map the message, so the two can't drift. That 404 is derived too; no live one has been captured.
- **One suggestion for every provider.** "This model is retired or not available to your API key. Choose another under Settings → Tools → OpenSpec → Direct API → Model, or in the tool window's model selector." The provider's message stays first and names the model.
- **The Claude case is derived, and labelled so.** It is built from the live Claude generation error `messages-401-invalid-key` (the existing `messages-403-permission-error` is itself derived, so it isn't used as a base). Only `error.type` changes, to the documented `not_found_error`, the message to a model-naming one, and the request id to `req_DERIVED`. The classifier keys on the type, never on the message wording. The fixtures README records it under derived variants, to be replaced by a live capture when a key is available.
  - *Alternative:* leave Claude out until a key exists. Rejected by the maintainer, because the most urgent retirement is a Claude model.

## Risks / Trade-offs

- [Claude's real 404 might differ from the derived body] → The classifier reads only `error.type`, which Anthropic's errors page documents for every 404, and the envelope around it is from a live Claude capture. A wording difference doesn't matter, and the README flags the case for re-capture.
- [Gemini's `NOT_FOUND` also covers a model that exists but can't generate text] → The suggestion says the model isn't available, and choosing another is the right fix there too.
- [A Claude 404 `not_found_error` for something other than a model or a workspace] → The plugin calls only `/v1/messages` and `/v1/models`, where neither is expected, so the case is accepted rather than guessed at.
