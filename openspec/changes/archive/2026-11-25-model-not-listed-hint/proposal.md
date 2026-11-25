# Proposal

## Why

Providers retire models and route their requests elsewhere. Google shuts down `gemini-2.5-pro` and `gemini-2.5-flash` on 2026-12-01 and sends their requests to other models, billed at the new model's rate. Until a request fails, nothing tells the user. Settings keeps naming the saved model, and requests keep succeeding on a model they didn't choose.

Two easier signals were already examined and rejected, as recorded on the linked tracker entry:
- **A notice on a `modelVersion` mismatch** would false-alarm on `-latest` aliases, and Google doesn't document what a rerouted response looks like.
- **A table of retired models** would be plugin-invented data that goes stale.

A new key that asks for a retired model already gets the provider's own 404, which the existing model-not-found hint explains.

One honest signal remains: the provider's own live model list. Settings already fetches it with the user's key when the user clicks Test. When the saved model isn't in that list, the provider is saying, in its own data, that the model is no longer one it offers this key. Checked live on 2026-11-24, Google's list still includes both 2.5 models and marks neither as deprecated. So the hint fires only once a provider drops a model from its list, and it never fires on a guess.

This is tracked in the linked tracker entry for routed or retired saved Gemini models.

## What Changes

- **A hint by the Model field in Settings.** It appears when the provider's live model list has been fetched successfully and the selected model isn't in it.
  - The wording: "*model* isn't in *Provider*'s current model list for this key. It may have been retired, or the provider may route its requests to another model. Choose a model from the list."
  - It names no replacement model: providers' own pages disagree on what replaces a retired model, so the provider's list is the answer.
- **When it applies:**
  - It updates as the selection changes.
  - It disappears when the selected model is in the list, when the fetch failed, and when no list was fetched. The static fallback list is never used to judge a model.
  - It shows nothing when the provider's response says more pages exist (Gemini's `nextPageToken`, Anthropic's `has_more`), because a partial list can't prove a model is missing.
  - It shows nothing once the provider, the key, or the Workspace, Org or Project fields change after the fetch, because the list may no longer describe that scope. A fetch that finishes after the provider was switched is ignored.
- **All three providers.**
  - OpenAI: checked live on 2026-11-24, the list has 103 models in one page, aliases (`gpt-4o`) beside their dated snapshots.
  - Anthropic: the list returns active and deprecated models and drops retired ones. A pre-4.6 alias (`claude-sonnet-4-5`) is listed only as its dated snapshot (`claude-sonnet-4-5-20250929`), so such an alias counts as listed when a dated snapshot of it is present.
  - Gemini: the list keeps only models that support `generateContent`, so a Gemini model outside it can't answer the plugin's requests either way. Its `-latest` aliases are listed (checked live on 2026-11-24).
- **No other surface changes.** There is no balloon or notification. The model-not-found error hint stays as it is, and the tool window's model selector is out of scope.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: a new requirement for the "not in the provider's model list" hint in Settings.

## Impact

- **Code:**
  - `AiModelOptions`: a pure check of whether a model is in a fetched list.
  - `OpenSpecSettingsPanel`: the hint label by the Model field, updated after a fetch and on selection changes, on the UI thread.
- **Tests:**
  - a plain test of the check;
  - a light Settings panel test using the captured provider model lists (`fixtures/ai/*/models-list*.json`, plus a live Gemini list captured for this change) and a successful-fetch seam.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unchanged.
- **Docs:** CHANGELOG, and the Direct API section of `docs/feature-reference.md`.
