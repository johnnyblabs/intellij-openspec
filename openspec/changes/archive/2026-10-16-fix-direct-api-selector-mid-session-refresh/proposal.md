# Fix: surface Direct API delivery + model selector when a provider is configured mid-session

## Why

The tool window builds its delivery-mode selector (`Clipboard`, `Editor Tab`, detected CLI tools, and `Direct API`) **once, when the panel is constructed**. The `Direct API` entry is only added if a provider key is already configured at that moment. If a user opens the tool window first and *then* configures a Direct API provider — the common first-run order — `Direct API` never appears in the delivery selector, and because the operation-time model selector is gated on Direct API being the active delivery mode, the model selector never appears either. The only recovery was restarting the IDE (or reopening the project) to force the panel to rebuild.

This made a freshly configured provider look broken: the user adds a key, the Test succeeds, but neither the Direct API delivery option nor the model picker shows up.

## What Changes

- When the Direct-API configured state changes at runtime, the tool window re-evaluates its delivery selector so the `Direct API` entry (and therefore the model selector) is offered immediately — without an IDE restart.
- Two trigger paths:
  - **Instant:** committing the AI configuration (Settings → Apply, or finishing the Setup Wizard) broadcasts a lightweight application event that the tool window listens for and refreshes on.
  - **Safety net:** any subsequent tool-window refresh (tree selection, the Refresh action, a file change) reconciles the delivery selector when the configured state has transitioned since it was last built.
- Rebuilding the delivery selector preserves the user's persisted delivery/tool choice — adding a key does not hijack the current selection; it only makes Direct API *available*.

## Capabilities

- `ai-integration` — MODIFIED: the operation-time model selection requirement gains a guarantee that the Direct API delivery option and its model selector surface when a provider is configured at runtime, not only when configured before the tool window was opened.

## Impact

- Tool window: the delivery selector is rebuilt on a runtime configuration transition; the model selector follows via its existing Direct-API gate.
- Settings and Setup Wizard: publish a configuration-changed event after committing the provider/key.
- No change to how operations resolve the model (still through the existing model setting), and no new provider/network behavior.
