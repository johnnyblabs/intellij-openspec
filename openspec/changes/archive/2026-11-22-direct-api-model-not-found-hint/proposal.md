## Why

The plugin keeps a saved Direct API model selection. When the provider retires that model, every generation fails with a bare HTTP 404 and no suggestion, because the error classifier has no kind for a missing model. `claude-sonnet-4-5` was the plugin's Claude default until 2026-09-08 and retires on 2026-11-30. Saved OpenAI selections such as `gpt-4o` and `gpt-4o-mini` retire in January 2027, and some older ones are already gone. This change is tracked in the linked tracker entry.

## What Changes

- **A model-not-found kind.** When a provider answers HTTP 404 and its documented error field says the model wasn't found, the plugin classifies the failure as model not found:
  - Claude: `error.type` `not_found_error`, except the workspace-not-found message, which keeps its own wording;
  - OpenAI: `error.code` `model_not_found`;
  - Gemini: `error.status` `NOT_FOUND`.
- **Its suggestion** says the model is retired or not available to this key, and to choose another in the Direct API settings or the tool window's model selector. The provider's own message, which names the model, is kept.
- **Real captures.** `scripts/capture-model-not-found.py` records each provider's 404, for a retired model and for an id that never existed, using the plugin's own request. OpenAI and Gemini are live captures. No Anthropic key was available, so the Claude case is a derived variant built from the live Claude error envelope with Anthropic's documented `not_found_error` type, and is marked for re-capture.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: a new requirement gives a missing or retired model its own suggestion on every Direct API surface.

## Impact

- `ErrorKind`, `DirectApiErrors` and their contract tests.
- New fixtures under `fixtures/ai/{claude,openai,gemini}/errors/`, the capture script, and the AI fixtures README.
- `docs/feature-reference.md` (Direct API errors) and `CHANGELOG.md`.
- Platform compatibility is unaffected: the plugin still supports IntelliJ IDEA 2024.2 and later.
