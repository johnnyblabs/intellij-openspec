# Design

## Root cause

`WorkflowActionPanel.populateToolSelector()` — which adds/withdraws the `Direct API` delivery entry based on `DirectApiService.isConfigured()` — runs **only in the constructor**. The refresh sink (`updatePipelineAndButton`) re-ran the *model* selector and its visibility, but never the *tool* selector. And `OpenSpecConfigurable.apply()` / the Setup Wizard committed the provider/key but signalled nothing to the tool window. So a configured-state change after construction was invisible to the delivery selector until the panel was rebuilt (IDE restart / project reopen).

## Approach: two complementary triggers

### 1. Instant — a configuration-changed event

A project-scoped message-bus topic `AiConfigListener` is published right after the AI config is committed:
- `OpenSpecConfigurable.apply()` (Settings → Apply), and
- `SetupWizardModel.persist()` (wizard finish).

The tool window subscribes on its disposable connection (`project.getMessageBus().connect(this)`, parented to the panel so it dies with the tab) and, on the event, calls a new public `WorkflowActionPanel.refreshDeliveryOptions()` on the EDT. That method unconditionally rebuilds the delivery selector and re-evaluates the model selector, so the affordance appears the moment Apply/Finish is clicked.

The publish happens **after** `markHasApiKey(...)`, which is synchronous, so `isConfigured()` is already current when the subscriber runs.

### 2. Safety net — a transition guard on refresh

`updatePipelineAndButton` (the EDT sink every refresh funnels through) compares the current `isConfigured()` against a `lastHasApi` field seeded at construction. On a **transition** it re-runs `populateToolSelector()` and updates `lastHasApi`. Guarding on the transition keeps the `detect()`/rebuild cost one-time rather than paying it on every DAG refresh. This covers configuration changes that arrive by any other path (e.g. a key removed, or a refresh racing ahead of the event).

## Why rebuilding does not hijack the user's selection

`populateToolSelector()` ends by calling `restoreToolSelection()`, which reads the **persisted** `preferredTool` / `preferredDeliveryMethod` and re-selects them; `onToolSelectionChanged` persists every live change, so persisted == live. The `updatingToolSelector` guard suppresses the action listener during the rebuild, so no spurious re-persist occurs. Adding a key therefore makes `Direct API` *available* in the list without changing which entry is selected — the user still chooses it. (When the persisted delivery method already is Direct API, restore re-selects it, and the model selector shows.)

## Threading

All selector mutation runs on the EDT: the transition guard sits inside `updatePipelineAndButton`'s `invokeLater`, and the topic handler wraps `refreshDeliveryOptions()` in `invokeLater`. `isConfigured()` is an EDT-safe cached read (the has-key cache is warmed off the EDT elsewhere).

## Testing

- **Headful (uiSmoke), the only layer that exercises the EDT combo wiring:**
  - happy path — a provider configured *before* first show surfaces the Direct API delivery option, the model selector, and the model in the status strip;
  - regression — with nothing configured at show, configuring mid-session and firing a refresh surfaces the Direct API option + model selector (this journey fails against the un-fixed code).
  - The test drives the plugin's own settings/credential gate via `@Remote` proxies (with the `plugin` id so the driver loads plugin classes); it flips the has-key cache without a keystore write and stores no key, so the model fetch short-circuits on the null key — no network, no keychain. The has-key flip is committed **last**, after the delivery method, so the one-shot transition guard cannot fire against half-committed settings.
- **Structural guards (unit):** the publish sites, the panel subscription + `refreshDeliveryOptions` call, and the transition-guard re-run — the wiring that behavioral tests on the EDT cannot reach.
