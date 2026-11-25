# Design

## Context

- **What Settings has.** It fetches the provider's model list with the user's key (`DirectApiService.listModels`; Test is the only fetch path in Settings) and resolves the editable Model selector with `AiModelOptions.resolve`. A saved model that the list lacks is kept: it's prepended, and nothing marks it.
- **What the parsers keep.** OpenAI's and Anthropic's lists are every model id the provider returns in its `data` array. Gemini's is the `models` entries whose `supportedGenerationMethods` includes `generateContent`, with the `models/` prefix stripped. None of the parsers follows a next page. The requests ask for the largest page: `pageSize=1000` for Gemini (default 50, capped at 1000) and `limit=1000` for Anthropic (default 20). OpenAI documents no pagination.
- **What each list holds, checked 2026-11-24:**
  - OpenAI, live: 103 models in one page, aliases beside their dated snapshots.
  - Gemini, live: 65 models in one page with no `nextPageToken`, the `-latest` aliases included, and `gemini-2.5-pro`/`-flash` still listed.
  - Anthropic, from its docs and the live fixture: active and deprecated models, retired ones dropped. For models before the 4.6 generation, the alias (`claude-sonnet-4-5`) is a pointer to a dated id and is listed only as that dated id (`claude-sonnet-4-5-20250929`).
- **Live check, 2026-11-24.** Google's list (65 models, every page read) still includes `gemini-2.5-pro` and `gemini-2.5-flash`, a week before their 2026-12-01 shutdown. `models.get` for `gemini-2.5-pro` carries no deprecation field; its description reads "Stable release (June 17th, 2025) of Gemini 2.5 Pro". So no live signal marks the coming retirement, and the hint can only follow the list.
- **Rejected on the tracker entry (2026-11-22):** a `modelVersion`-mismatch notice, because `-latest` aliases change by design and a rerouted response's shape is undocumented; and a table of retired models, because that is invented data that goes stale.

## Goals / Non-Goals

**Goals:**
- Tell the user, in Settings, when the provider's own list no longer offers their saved model.
- Never false-alarm: only a successful live fetch can produce the hint.

**Non-Goals:**
- Naming a replacement model.
- Predicting a retirement before the provider acts.
- The tool window's model selector, or a balloon.

## Decisions

### 1. The check is pure and lives with the resolver

`AiModelOptions.notListed(provider, model, fetched, fetchOk, complete)` answers true only when all of these hold:
- the fetch succeeded;
- it was complete (the response had no `nextPageToken` and no `has_more: true`);
- the list isn't empty, and the model isn't blank;
- the model isn't in the list (after stripping a `models/` prefix, since the plugin's Gemini ids carry none);
- for Claude, no dated snapshot of it (`<model>-YYYYMMDD`) is in the list.

It is a plain, platform-free function. `listModels` reports completeness alongside the ids: each parser reads its provider's page marker.

### 2. The hint is a label by the Model field, driven by the last fetch

- **What it holds:** the panel keeps the last successful fetch's list and its completeness, or nothing.
- **When it updates:**
  - `applyModelOptions` records the list first (or nothing on a failed fetch), then repopulates the combo, then refreshes the hint. Repopulating fires selection events, so the order matters.
  - A selection listener on the Model combo refreshes the hint too.
- **When it clears:**
  - a provider switch (`onProviderChanged`, which also covers `setAiProvider` and reset);
  - an edit to the key, Workspace, Org or Project fields.
- **A fetch that finishes late:** forgetting the list bumps a fetch generation. The worker's `done()` returns early when the generation changed since Test started, so a fetch from before a provider switch or a key edit can't apply its result.
- **Where it runs:** both calls already run on the UI thread, so no new threading is needed.
- **Appearance:** the label uses the panel's existing comment style and a warning colour, with the wording from the proposal.

### 3. The cached list from the tool window doesn't count

The per-session cache (`getDiscoveredModels`) may come from a different key or settings. The hint is driven only by a fetch made by this Settings panel for the selected provider, so it can't disagree with what the panel shows.

## Risks / Trade-offs

- **[A provider keeps listing a retired model]** No hint appears, as now. → That is the provider's own statement; a request still succeeds, and the model-not-found hint covers a refusal.
- **[A provider drops a model that still works]** The hint appears and the request still succeeds. → The wording says "may", and the provider's list is the provider's current offer.
- **[OpenAI fine-tunes]** Whether OpenAI's list includes fine-tuned models is undocumented, and the checked account has none. → A fine-tune missing from the list would get the hint. The wording says "may"; if it happens, the user can ignore it, and it is recorded as unverified.
- **[Anthropic's deprecated models]** They stay listed until retirement, so the hint fires only after retirement, when the existing model-not-found hint also applies. → That is the provider's own timing.

## Migration Plan

Additive UI. No settings change. Rollback is reverting the change.

## Open Questions

None.
