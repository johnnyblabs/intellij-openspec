## Context

CLI detection runs once at project startup via `StartupDetection` (a `ProjectActivity`). The result is cached in `CliDetectionService` volatile fields. The status bar in `OpenSpecToolWindowPanel` reads the cached value — it never triggers re-detection. The Refresh toolbar button calls `refreshAsync()` �� `updateCliStatus()` which also only reads the cache. The only way to re-detect is the "Detect" button in Settings.

## Goals / Non-Goals

**Goals:**
- Re-run CLI detection automatically when the user activates the OpenSpec tool window
- Throttle detection to avoid spawning processes on rapid focus changes
- Update the status bar after re-detection

**Non-Goals:**
- Changing the startup detection flow (it remains as-is)
- Watching for PATH changes or file system events for CLI installation
- Adding a visible "re-detecting..." spinner (detection is fast enough to be invisible)

## Decisions

**Add `detectIfStale()` to `CliDetectionService`.** This method checks an `Instant` timestamp and skips detection if the last run was within 30 seconds. This keeps the throttling logic in the detection service where it belongs, rather than in UI code.

*Alternative: Throttle in the listener.* Rejected — puts timing logic in the wrong layer and would need to be duplicated if other callers want throttled detection.

**Use `ToolWindowManagerListener.toolWindowShown` for the activation hook.** This IntelliJ listener fires when a tool window becomes visible. It's registered via `project.getMessageBus().connect()` in `OpenSpecToolWindowFactory.createToolWindowContent()`, which is the natural place since it already has access to the project and tool window.

*Alternative: `ContentManagerListener` or focus listener on the panel itself.* Rejected — more complex wiring and less reliable for tab switching scenarios.

**Run detection on a pooled thread, update status on EDT.** Detection calls `detect()` which spawns processes (10s timeout each). This must not block the EDT. After detection, `SwingUtilities.invokeLater` updates the status bar.

**Expose `updateCliStatus()` on `OpenSpecToolWindowPanel` as package-private.** The listener needs to call it after re-detection. Currently it's private. Making it package-private (default access) keeps the API minimal while allowing the factory's listener to trigger the update.

*Alternative: Call `refresh()` which calls `updateCliStatus()` internally.* Rejected — `refresh()` rebuilds the entire tree model, which is heavyweight for a simple status bar update.

## Risks / Trade-offs

**[Risk] Detection spawns up to 4 processes (settings path, bare, login shell, common paths)** → Throttling at 30s ensures this happens at most twice per minute. Detection short-circuits on first success, so the common case (CLI found) is one process.

**[Risk] Tool window shown fires on tab switches within the tool window** → The listener filters on `toolWindow.getId().equals("OpenSpec")` and throttling prevents redundant work.