## Context

The plugin programmatically invokes `AnAction` instances from 3 UI panels (GettingStartedPanel, OpenSpecToolWindowPanel, WorkflowActionPanel). Each call site manually constructs a `DataContext`, clones a `Presentation`, builds an `AnActionEvent`, and then calls `ActionUtil.performActionDumbAwareWithCallbacks(action, event)`. This method is deprecated in the IntelliJ Platform SDK.

All 4 call sites follow an identical pattern — only the `place` string and `DataContext` differ.

## Goals / Non-Goals

**Goals:**
- Replace all 4 deprecated `performActionDumbAwareWithCallbacks` calls with `ActionUtil.invokeAction()`
- Eliminate manual `AnActionEvent` construction where the new API handles it internally
- Pass Plugin Verifier with zero deprecation warnings for `performActionDumbAwareWithCallbacks`

**Non-Goals:**
- Refactoring action invocation into a shared utility method (only 4 call sites, not worth the abstraction)
- Addressing other Plugin Verifier warnings (separate changes)

## Decisions

### Use `ActionUtil.invokeAction(AnAction, DataContext, String, InputEvent)`

The `invokeAction` overload accepts a `DataContext`, a `place` string, and an optional `InputEvent`. It internally constructs the `AnActionEvent`, calls `update()` to check action availability, and then performs the action with proper dumb-mode awareness.

**Alternative considered**: `ActionUtil.invokeAction(AnAction, Component, String)` — rejected because some call sites need to pass `PROJECT` via a custom `DataContext` and don't have a suitable component reference.

### Keep existing DataContext lambdas

Three of the 4 call sites provide `project` via a lambda `DataContext`. The `onStartNewChange()` call site uses `DataContext.EMPTY_CONTEXT`. These remain unchanged — `invokeAction` accepts any `DataContext`.

### Pass `null` for InputEvent

All current call sites pass `null` for the `InputEvent` in the `AnActionEvent` constructor. The `invokeAction` method accepts a nullable `InputEvent`, so we continue passing `null`.

## Risks / Trade-offs

- **[Low] `update()` now called before `actionPerformed()`** → This is actually a correctness improvement. If an action's `update()` disables it, it won't fire. Current code skips `update()`, which could invoke disabled actions. No mitigation needed — this is desired behavior.
- **[Low] Presentation no longer cloned manually** → `invokeAction` handles presentation internally. No user-visible change.
