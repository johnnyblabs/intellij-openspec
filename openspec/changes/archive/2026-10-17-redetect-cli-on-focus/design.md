## Context

See proposal.md — Why. Current code:
- `CliDetectionService` caches `detectedVersion`. `detectIfStale()` re-runs detection only if the last run was at least 30 s ago (`DETECTION_STALENESS`) and returns whether it ran.
- `OpenSpecToolWindowFactory` subscribes `ToolWindowManagerListener.toolWindowShown`. For the OpenSpec window it calls `detectIfStale()` on a pooled thread and, if it ran, calls `OpenSpecToolWindowPanel.updateCliStatus()` on the EDT through `invokeLater`. It also runs a best-effort `WorkflowProfileService.refresh()`.
- The detection service takes an injectable process runner, so a version change can be simulated in tests.

## Goals / Non-Goals

**Goals:** a mid-session CLI upgrade shows up when the user returns to the IDE, with no extra CLI runs beyond the existing 30-second limit.

**Non-Goals:**
- Periodic polling, which costs CPU and runs a process with nobody watching.
- Re-checking staleness inside every version-gated decision. That was an optional idea on the tracker; it touches many call sites, and focus-regain covers the real case. Revisit only if a concrete gap appears.

## Decisions

**1. Listen for the IDE regaining focus, scoped to the tool window.** Subscribe `ApplicationActivationListener.TOPIC` on the application message bus, connected with the tool window's disposable so the subscription ends with the window. In `applicationActivated(IdeFrame frame)`, act only when `frame.getProject() == project`, so each project refreshes when its own window is focused. *Alternative considered:* an application-level listener in `plugin.xml` that loops over every open project. Rejected: it refreshes projects the user isn't looking at, and it needs its own lookup of each project's tool window.

**2. One extracted refresh routine for both triggers.** Move the existing `toolWindowShown` body (pooled `detectIfStale()` → `invokeLater` `updateCliStatus()`, plus the profile refresh) into a single method. Call it from both listeners, so the two paths can't drift. The 30-second throttle lives in `detectIfStale()`, so both triggers share it.

**3. Keep the focus decision pure and testable.** A tiny static predicate (e.g. `refreshesOnActivation(IdeFrame frame, Project project)`) decides whether an activation applies to this project. A unit test with a mocked frame covers it, including a `null` project on a welcome-screen frame. Detection picking up a new version is tested through the service's injectable process runner: the version changes, it's stale, `detectIfStale()` runs, and the new version is reported. It's also tested inside the throttle window: no re-run.

## Risks / Trade-offs

- **Risk: CLI runs on every alt-tab.** → **Mitigation:** the existing 30-second `detectIfStale()` limit, covered by a test.
- **Risk: work on the UI thread.** → **Mitigation:** detection stays on a pooled thread and only the label update goes to `invokeLater`, the same as today's tool-window-shown path.
- **Risk: a subscription leak.** → **Mitigation:** connect with the tool window's disposable. (The existing `toolWindowShown` subscription uses the project bus with no disposable; that's tied to the project lifetime and left unchanged here.)

## Migration Plan

Additive. Rollback means reverting the commit.
