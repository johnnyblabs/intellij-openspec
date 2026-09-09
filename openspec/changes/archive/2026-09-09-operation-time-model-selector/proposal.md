## Why

The AI model is Settings-only today — a single global `OpenSpecSettings.aiModel`, edited only in Settings → Tools → OpenSpec → Direct API. The **delivery mode**, by contrast, is already a live selector in the tool-window header (persisted, shown in the status strip, read at operation time). That asymmetry splits the two Direct-API knobs across two surfaces: you can switch delivery mode in one click but must open Settings to switch models. The just-shipped dynamic model discovery (the Model list is now fetched live with the user's key) makes an operation-surface model picker genuinely useful rather than a stale dropdown — so surface the model where the work happens.

## What Changes

- Add a **model dropdown to the tool-window header**, next to the delivery selector, mirroring that selector's shape (editable, persisted, surfaced in the status strip).
- The dropdown is **visible/enabled only when Direct API is the active delivery mode** (a model is meaningless for Clipboard/Editor-Tab delivery).
- It is **populated from the live model list** (fetched off the EDT via the existing list-models call, reusing the discovery from `fetch-provider-models-dynamically`) with the static provider list as the immediate, no-network fallback; it stays editable so any model can be typed.
- Selecting a model **persists to the existing `aiModel` setting**, so operations (generate / Fast-Forward / Continue / Explore) pick it up through the existing model-resolution funnel — no operation is prompted mid-flow, preserving the one-click generate UX.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `ai-integration`: ADD an "Operation-time model selection" requirement — the model is selectable in the tool window at operation time (not only in Settings), the selection persists to the configured model and is what operations use, the selector is Direct-API-only and editable, and it is populated from the live model list with a static fallback.

## Impact

- **Code:** `toolwindow/WorkflowActionPanel.java` (new model combo mirroring `toolSelector` — field, `getSelectedModel()`, change-listener writing `settings.setAiModel(...)`, populate, Direct-API-only visibility, status-strip inclusion); `ai/DirectApiService.java` (a transient in-memory per-session `discoveredModels` cache so the combo and Settings Test share one fetch — not persisted).
- **Reuse, unchanged:** `ai/AiModelOptions.resolve(...)`, `DirectApiService.listModels(...)`, `OpenSpecSettings.getAiModel/setAiModel`.
- **No service-signature changes:** every Direct-API operation already resolves the model through `DirectApiService.getModel(provider)` → `settings.getAiModel()`; the combo writes that setting, so `generate`/`generateRaw`/`generateAllRemaining` are untouched.
- **Platform:** no new IntelliJ API (JComboBox + existing `ApplicationManager`/`ModalityState`); the live fetch runs off the EDT and the combo mutation on the EDT (the pattern from `harden-direct-api-flow`/the settings Test). No CLI surface — model is purely a plugin Direct-API concept.
- **Tests:** status-strip content (model shown when Direct API), the session cache (one fetch serves the session), and a structural guard for the off-EDT fetch / on-EDT populate / persist-to-`aiModel` / Direct-API gating.
