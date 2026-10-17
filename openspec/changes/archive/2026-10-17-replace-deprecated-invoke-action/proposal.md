## Why

The Plugin Verifier reports five usages of a deprecated platform method, `ActionUtil.invokeAction(AnAction, DataContext, String, InputEvent, Runnable)`, on IntelliJ 2024.3, 2025.1 and 2025.2. They are the plugin's only reported deprecations. The CI spec allows a plain deprecation only while no non-deprecated replacement exists at the compatibility floor (2024.2). One now does: `ActionManager.tryToExecute(...)` exists and is not deprecated on every IDE in the range. So these usages should be migrated.

Tracked in the linked tracker entry.

## What Changes

- **One helper replaces five copies.** Each of the five sites looks up a registered action, hand-builds a `DataContext` that only answers the project, and calls the deprecated method. They are:
  - the deltas-preview diff link;
  - the "No active changes" tree hint;
  - the Getting Started "Propose a Change" button;
  - the workflow panel's "Propose a change" link;
  - the Setup Wizard's propose-on-finish.

  All five now call a small helper that runs the action through `ActionManager.tryToExecute(action, null, sourceComponent, place, true)`. That call works on 2024.2 through 2025.2, takes the **real UI data context** from the source component instead of a hand-made one, and still runs the action's `update()` first, so a disabled action does not run.
- **Propose and the delta diff stay usable while the IDE is indexing.** The new call path silently skips actions that aren't dumb-aware during indexing, where the old call showed a "not available while indexing" balloon. Both actions are marked dumb-aware so the links and buttons keep working. The plugin uses no index APIs, so this is safe.
- **A guard test** fails the build if the deprecated call reappears in the plugin's sources.
- **No other user-visible change.** The same actions open from the same links and buttons.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `workflow`: add a requirement that actions launched from in-panel links and buttons run through the platform action pipeline (data context from the source component, `update()` honored) and that Propose and the delta diff are available during indexing.

## Impact

- **Code:**
  - `OpenSpecToolWindowPanel` (two sites), `GettingStartedPanel`, `WorkflowActionPanel` and `SetupWizardDialog`.
  - A new small action-invocation helper.
  - `OpenSpecProposeAction` and `DeltaSpecDiffAction` become dumb-aware.
  - A stale comment in `build.gradle.kts` (the removal-canary note that mentions these deferred calls).
- **Tests:** a headless helper test (runs when enabled, skipped when disabled) and a static guard against the deprecated call.
- **Platforms:** IntelliJ 2024.2+ compatibility unchanged. The replacement exists and is not deprecated on 2024.2, 2024.3, 2025.1 and 2025.2. `verifyPlugin` is the gate: it must report **zero** deprecated usages.
