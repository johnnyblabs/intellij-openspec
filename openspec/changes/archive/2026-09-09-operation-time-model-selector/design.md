## Context

See proposal.md for motivation. Two seams already exist and are reused wholesale:
- The **delivery selector** (`WorkflowActionPanel.toolSelector`) is a persisted, tool-window-header combo with a `getSelectedDeliveryMode()` getter, an `onToolSelectionChanged()` writer (persists to settings), a `restoreToolSelection()`, and a `deliveryModeStatusLabel` in the status strip. The new model combo mirrors this shape exactly.
- **Model resolution** for every Direct-API operation funnels through one method — `DirectApiService.getModel(provider)` → `settings.getAiModel()` (fallback `provider.getDefaultModel()`). `generate`, `generateRaw`, and Generate-All all read it; nothing bypasses it.
- The **dynamic model list** (`DirectApiService.listModels`) and the pure `AiModelOptions.resolve(...)` populate/preserve helper shipped in `fetch-provider-models-dynamically`.

## Goals / Non-Goals

**Goals:** switch the model at operation time from the tool window in one click; the selection drives all Direct-API operations; the picker reflects the account's live models; the fast one-click generate flow is untouched.

**Non-Goals:** per-operation modal prompts; a `model` parameter on the generation service methods; persisting the fetched model *list* across sessions (an in-memory session cache only); changing the default provider or the generation request/response contract.

## Decisions

**1. The combo writes `aiModel`; operations read it — no threading.** Because all operations resolve the model through `getModel()` → `settings.getAiModel()`, the header combo only needs to `settings.setAiModel(getSelectedModel())` on change (exactly as `onToolSelectionChanged` persists `preferredTool`/`preferredDeliveryMethod`). `generate`/`generateRaw`/`generateAllRemaining`/`getModel` are untouched. This is the load-bearing simplification that keeps the change small.
- *Alternative — thread a `model` param through the three service methods + all call sites:* rejected as unnecessary churn; the single existing funnel already carries the value.

**2. Mirror `toolSelector` for the UI.** New `JComboBox<String> modelSelector` in the header row, editable, with `getSelectedModel()`, `onModelSelectionChanged()`, `populateModelSelector()`, and restore-from-`settings.getAiModel()` on construct/refresh. Fold the model into the status strip string (`… · <model> · <deliveryMode>`) when Direct API is active. Gate visibility/enablement on `getSelectedDeliveryMode() == DIRECT_API` (+ provider configured), hooked into `onToolSelectionChanged` and the DAG refresh where the delivery label already updates.

**3. Populate from the live list, in-memory only.** Immediate fill = `AiModelOptions.resolve(null, false, provider.getModels(), settings.getAiModel())` (static + preserved current, never empty, no network). When the combo is relevant (Direct API + `isConfigured()`), fetch off the EDT via `listModels(provider, key)` and repopulate through `invokeLater(..., ModalityState)` — the exact pattern from `OpenSpecSettingsPanel.testApiConnection`/`applyModelOptions`. A transient `Map<AiProvider,List<String>> discoveredModels` on `DirectApiService` (written on every successful `listModels`, including a Settings "Test") lets the combo and Settings share one fetch per session; it is **in-memory only** (honors the `fetch-provider-models-dynamically` "no cross-session caching" non-goal). Cold cache → one off-EDT fetch; warm → instant.
- *Alternative — static list only:* rejected; the picker would be strictly worse than the dynamic Settings picker and would not deliver the "select your real model at operation time" value.

## Risks / Trade-offs

- **Two live views onto `aiModel`** (Settings combo + tool-window combo). Last writer wins; the tool-window combo writes directly (like the delivery selector) while Settings is Apply-gated. On DAG refresh the tool-window combo re-reads `aiModel`, so a Settings Apply is reflected. Rare unsaved-Settings-vs-tool-window edge case is acceptable (same as the delivery selector today).
- **A fetch when the combo becomes relevant** adds a background GET; guarded by `isConfigured()` and served from the session cache after the first fetch, so it is at most one GET per provider per session.
- **`aiModel` is provider-agnostic** (one value). The combo shows the currently-configured provider's models + the current selection; switching provider (a Settings action) repopulates it. No per-provider model memory — out of scope.

## Open Question (decide at apply)

Whether to add an explicit "refresh models" affordance to the header combo, or rely solely on auto-fetch-when-relevant + the session cache. Default: auto-fetch + cache (no extra control); add a refresh only if the auto-fetch timing feels stale in the sandbox check.
