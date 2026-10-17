## Context

See proposal.md — Why. All five call sites share one shape: `ActionManager.getAction(...)` (or `new DeltaSpecDiffAction(path)`), then `DataContext ctx = dataId -> PROJECT.is(dataId) ? project : null`, then `ActionUtil.invokeAction(action, ctx, place, null, null)`. They all run on the EDT: four from Swing listeners and the wizard from `SwingUtilities.invokeLater` after `super.doOKAction()`.

The jetbrains-platform-guru checked the candidates (javap on the 2024.2 jars, plus intellij-community sources for 243, 251 and 252):

| Candidate | 242 | 243 / 251 | 252 |
|---|---|---|---|
| `ActionUtil.invokeAction(AnAction, AnActionEvent, Runnable)` | missing (`NoSuchMethodError`) | ok | deprecated |
| `ActionUtil.performAction(AnAction, AnActionEvent)` | missing | missing | new |
| `ActionUtil.performActionDumbAwareWithCallbacks` | ok | ok | deprecated |
| Building an `AnActionEvent` ourselves | `createEvent` missing | ok | every 242 factory is **forRemoval** |
| **`ActionManager.tryToExecute(AnAction, InputEvent, Component, String, boolean)`** | **ok** | **ok** | **ok, unchanged** |

## Goals / Non-Goals

**Goals:**
- Zero deprecated usages reported by `verifyPlugin` on 242–252.
- The same behaviour from the user's point of view, except Propose and the diff now also work during indexing.

**Non-Goals:**
- Raising the compatibility floor.
- Adding UI smoke journeys for these paths. None exist today, and a manual sandbox pass covers them for this change; whether to add a journey is a separate call.

## Decisions

**1. `ActionManager.tryToExecute(action, null, component, place, true)` behind one helper.** It's the only API that exists and is non-deprecated across the whole range. `now = true` runs synchronously on the EDT like the old call; `false` would defer until focus settles. A tiny package-level helper keeps the API choice in one place, so the next platform shift is a one-line change. *Alternative considered:* building an `AnActionEvent` and calling an `ActionUtil` method. Rejected: those methods are missing on 242, and the event factories are scheduled for removal on 252, which would swap a plain deprecation for a forRemoval one and trip the removal canary.

**2. Pass the real component, not a hand-made `DataContext`.** `tryToExecute` derives the data context from the component through `DataManager`, which resolves the project from the frame, and runs a proper update session (correct for the plugin's background-thread `update()`). Components:
- the preview pane for the diff link;
- the tree for the hint;
- the button for Getting Started;
- the hyperlink label for the workflow panel;
- for the wizard, which is already disposed when the action runs, the project's frame from `WindowManager.getFrame(project)`. Not `null`: that would fall back to whichever window has focus, which may be another project.

`inputEvent` stays `null`; neither action reads it.

**3. Mark `OpenSpecProposeAction` and `DeltaSpecDiffAction` dumb-aware.** During indexing, the update session disables non-dumb-aware actions, so the click would do nothing with no feedback, where the old path showed a balloon. Neither action touches indexes, and the plugin uses no index APIs at all; `OpenSpecValidateFromProjectViewAction` is already dumb-aware. *Alternative considered:* attaching a "not available while indexing" hint to the rejected callback. Rejected: the actions genuinely work during indexing, so blocking them would be wrong.

**4. A static guard against regression.** A test, in the same spirit as the documentation hygiene test, fails if `src/main/java` contains `ActionUtil.invokeAction`. The verifier stays the real gate, because `build`/`test` compile against 242, where the method isn't deprecated.

## Risks / Trade-offs

- **Risk: the wizard's propose runs against the wrong project.** → **Mitigation:** pass the project frame explicitly, never `null`. Check it in the sandbox with two projects open if feasible.
- **Risk: `tryToExecute` skips an action whose `update()` the hand-made context used to satisfy.** → **Mitigation:** the real context is a superset (the project resolves through the frame). The sandbox pass clicks every site.
- **Risk: 243–252 confidence comes from source, not jars.** → **Mitigation:** `verifyPlugin` against all four IDEs is the gate. Grep its report for any remaining `invokeAction` and for "will be removed".

## Migration Plan

No data or config. Rollback means reverting the commit.
