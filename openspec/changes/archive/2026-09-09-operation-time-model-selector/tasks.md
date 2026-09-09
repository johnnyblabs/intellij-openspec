## 1. Session model cache (`DirectApiService`)

- [x] 1.1 Add a transient in-memory `Map<AiProvider, List<String>> discoveredModels` (not persisted). Write it on every successful `listModels(...)` (so a Settings "Test" warms it too). Add an accessor the tool window can read (returns the cached list for a provider, or null/empty when cold).

## 2. Tool-window model selector (`WorkflowActionPanel`)

- [x] 2.1 Add an editable `JComboBox<String> modelSelector` to the header row next to `toolSelector`, with `getSelectedModel()` (mirror `getSelectedDeliveryMode()`) and an `onModelSelectionChanged()` that calls `settings.setAiModel(getSelectedModel())` (mirror `onToolSelectionChanged`).
- [x] 2.2 `populateModelSelector()` — fill via `AiModelOptions.resolve(discovered, fetchOk, provider.getModels(), settings.getAiModel())`: immediate static fill (no network), then an off-EDT `listModels(...)` fetch (guarded by `isConfigured()`), served from the session cache when warm, repopulated via `invokeLater(..., ModalityState)`. Restore the selection from `settings.getAiModel()` on construct/refresh.
- [x] 2.3 Gate visibility/enablement on `getSelectedDeliveryMode() == DIRECT_API` (and provider configured); hide/disable for Clipboard/Editor-Tab. Hook into `onToolSelectionChanged` and the DAG refresh where the delivery label is recomputed.
- [x] 2.4 Fold the model into the status strip when Direct API is active (`… · <model> · <deliveryMode>`) — update `buildStatusStripText(...)` and the live update path.

## 3. Tests

- [x] 3.1 Extend `WorkflowActionPanelTest.buildStatusStripText` cases: model shown when Direct API, omitted otherwise.
- [x] 3.2 Session-cache unit test (`DirectApiServiceTest`, MockedStatic pattern): a successful `listModels` warms `discoveredModels`; a second read returns the cached list without a network call.
- [x] 3.3 Extend `DirectApiFlowStructuralGuardTest`: the model fetch runs off the EDT (`executeOnPooledThread`), the combo mutation is on the EDT, selecting a model calls `settings.setAiModel(...)`, and the model combo is gated on `DIRECT_API`.
- [x] 3.4 Where the populate/visibility decision can be extracted into a pure helper, unit-test it (reuse `AiModelOptions.resolve` coverage rather than duplicating).

## 4. Docs

- [x] 4.1 CHANGELOG `## Unreleased → Added`: pick your model from the tool window (next to the delivery selector), populated from your account's live models; Direct-API-only.
- [x] 4.2 Update `docs/feature-reference.md` (Direct API / tool-window section) to note operation-time model selection. Verify `DocumentationHygieneTest` stays green.

## 5. Verify

- [x] 5.1 **test-engineer PLAN** at apply start — split pure-unit (status strip, session cache, populate helper) from what only a sandbox/`uiSmoke` can show (header combo, Direct-API-only visibility).
- [x] 5.2 `./gradlew build` green (test + JaCoCo floor; ratchet if it rises).
- [x] 5.3 Confirm no new `com.intellij.*` API (JComboBox + existing ApplicationManager/ModalityState) → `verifyPlugin` not needed; optional sandbox `runIde` to eyeball the combo + visibility + status strip.
- [x] 5.4 **intellij-code-reviewer** after implementation (touches the tool-window EDT path + `DirectApiService`).
