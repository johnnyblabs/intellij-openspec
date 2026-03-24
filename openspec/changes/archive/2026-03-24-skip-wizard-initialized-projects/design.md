## Context

`OpenSpecToolWindowFactory.createToolWindowContent()` runs two checks sequentially:

1. `GettingStartedPanel.detectState()` — filesystem-based, correctly returns `READY` when `openspec/` exists with changes or archives
2. `settings.isSetupCompleted()` — persisted flag, defaults to `false`

When a project is already initialized but opened for the first time in the IDE, check 1 passes but check 2 fails, causing the wizard to launch and rebuild the tool window content — overriding the correct tree view.

## Goals / Non-Goals

**Goals:**
- Skip the wizard when the project is already in READY state
- Auto-persist `setupCompleted = true` so the flag stays consistent with filesystem reality
- Preserve wizard behavior for genuinely uninitialized projects

**Non-Goals:**
- Changing the wizard itself
- Modifying `GettingStartedPanel.detectState()` logic (it's already correct)
- Handling edge cases like corrupted openspec directories

## Decisions

**Gate the wizard on READY state, not just the flag.**

In `OpenSpecToolWindowFactory.createToolWindowContent()`, when `!setupCompleted` is true, check if `state == READY` first. If so, auto-set `setupCompleted = true` and skip the wizard. Only launch the wizard for non-READY states.

*Alternative considered:* Setting `setupCompleted` in `StartupDetection` — rejected because startup activity runs asynchronously and may race with tool window creation.

*Alternative considered:* Removing the `setupCompleted` flag entirely and always using `detectState()` — rejected because the flag serves a purpose for non-READY states (preventing the wizard from re-showing after cancel).

**Promote Apply and Compliance to the icon bar, scope overflow to change-only.**

The icon bar represents the workflow progression for the active change: Apply → Compliance → Verify → Archive. Creation actions (Start New Change, Fast-Forward) and global actions (Archive All Changes) belong in the top-level menu, not in a change-scoped context. The overflow menu is reduced to Sync Specs and Cancel Generation — the only remaining change-scoped actions not in the icon bar.

## Risks / Trade-offs

**[Risk] User misses AI configuration on pre-initialized projects** → Acceptable. The user can always run the wizard manually via OpenSpec > Setup Wizard. The tree view is more valuable than forcing configuration.

**[Risk] Users expect Start New Change in overflow** → Low risk. It's in the top-level OpenSpec menu and toolbar. The overflow context is anchored to `[change-name]` — creation actions don't belong there.
