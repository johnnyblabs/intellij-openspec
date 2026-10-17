# Tasks

## 1. Surface Direct API on a runtime configuration change

- [x] 1.1 Add a project-scoped `AiConfigListener` message-bus topic (`aiConfigChanged()`).
- [x] 1.2 Publish it from `OpenSpecConfigurable.apply()` after the AI config is committed.
- [x] 1.3 Publish it from `SetupWizardModel.persist()` after the AI config is committed.
- [x] 1.4 Add public `WorkflowActionPanel.refreshDeliveryOptions()` that rebuilds the delivery selector and re-evaluates the model selector on the EDT, preserving the persisted selection.
- [x] 1.5 Subscribe the tool window to the topic on its disposable connection and call `refreshDeliveryOptions()` via `invokeLater`.
- [x] 1.6 Add a transition guard in `updatePipelineAndButton` that re-runs `populateToolSelector()` only when `isConfigured()` transitions since last built (seed `lastHasApi` at construction).

## 2. Make the selectors locatable for headful tests

- [x] 2.1 Give the delivery and model combos stable accessible names.

## 3. Tests

- [x] 3.1 uiSmoke journey — Direct API configured **before** first show: delivery option + model selector + model-in-status-strip present (happy path; passes on current code). Each test SHALL fail if the corresponding wiring is broken.
- [x] 3.2 uiSmoke journey — configuration applied **mid-session** + refresh surfaces the Direct API option + model selector (regression; fails against the un-fixed code).
- [x] 3.3 Structural-guard unit tests — publish sites (`apply()`, wizard `persist()`), panel subscription + `refreshDeliveryOptions` call, and the transition-guard re-run.
- [x] 3.4 `./gradlew build` green (unit tests + JaCoCo coverage floor); both uiSmoke journeys green.
