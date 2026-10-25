## Context

- **Send:** `ExplorePanel.submitTopic()` disables the input, then calls `ExploreContextAction.runExploreDirect`, which builds the prompt and calls `deliverDirectApi`.
- **Unconfigured provider:** `deliverDirectApi` copies to the clipboard (through `deliverClipboard`, which shows its own balloon), shows a second balloon, and returns without touching the panel.
- **Configured provider:** it shows the loading state and runs a `Task.Backgroundable`. That task catches only `AiApiException`. The panel's `showResult` and `showError` re-enable the input; nothing else does.
- **Where the tab comes from:** the panel is created by `OpenSpecToolWindowFactory` or `ExplorePanelService.getAndActivate()` and lives as long as the tool window, so it outlives a provider change in Settings.

## Goals / Non-Goals

**Goals:**
- No path from Send leaves the input disabled.
- The fallback is explained in the panel, without balloons.

**Non-Goals:**
- Hiding or rebuilding the Explore tab when the provider changes.
- Multi-turn conversation, context or instruction changes. Those belong to the other Explore tracker entries.
- The modal-dialog path for the clipboard and editor-tab delivery modes.

## Decisions

1. **The fallback shows a notice in the panel.**
   - **Panel:** `ExplorePanel.showNotice(topic, message)` renders muted, non-error text with the topic header and re-enables the input.
   - **Action:** `deliverDirectApi` uses it when `panelService.getExplorePanel()` is non-null, and shows no balloon.
   - **No panel:** it shows one balloon. `deliverClipboard` is split into `copyToClipboard(prompt)` and the balloon, so the fallback doesn't stack two.
   - **The text:** "No Direct API provider is configured, so the prompt was copied to the clipboard. Paste it into your AI tool, or configure a provider in Settings → Tools → OpenSpec → Direct API."
   - **Why the panel:** that's where the user is looking. The notification design tracker entry asks for inline feedback over balloons.
2. **Every task end is handled.**
   - **`run()`:** catches `AiApiException` as now. It also catches `RuntimeException`: `ProcessCanceledException` is rethrown, and anything else is logged at WARN and shown with `showError(topic, "Explore failed: " + message)`.
   - **`onCancel()`:** shows `showNotice(topic, "Cancelled.")`.
   - **`onFinished()`:** runs on the EDT after success, cancel or throw, and calls `panel.restoreInput()`, which re-enables the input. It is idempotent with `showResult` and `showError`, which already re-enable it. It is the `finally` the tracker entry asks for.
   - *Alternative:* a `try/finally` inside `run()` that posts `invokeLater`. Rejected, because it misses a cancel thrown before `run()` and duplicates what the task's EDT callbacks already guarantee.
3. **The clipboard goes through `CopyPasteManager.getInstance().setContents(new StringSelection(prompt))`.**
   - **Why:** AWT's `Toolkit.getSystemClipboard()` throws `HeadlessException` in headless tests and bypasses the IDE's clipboard history.
   - **Scope:** the Copy Response button moves to it too, for consistency.
4. **Test seams, all `@TestOnly`:**
   - **The request:** `ExploreContextAction.setRawRequestForTest(ThrowingFunction<String, String>)` replaces the `DirectApiService.generateRaw` call (the service is `final`), plus a matching configured-check override.
   - **The panel:** `ExplorePanel.sendButtonForTest()`, `isInputEnabledForTest()` and `responseTextForTest()`.

   The tests click the real Send button, so the real `submitTopic` → `runExploreDirect` → `deliverDirectApi` path runs.

## Risks / Trade-offs

- **The order of `onFinished` and the panel's own `invokeLater` hop:** `showResult` and `showError` post a second `SwingUtilities.invokeLater`, so `onFinished` may run before them. → Both only enable, so the final state is enabled either way. The tests flush the EDT queue before asserting.
- **Cancel during a long provider call:** `generateRaw` doesn't check the indicator, so cancel takes effect only when the HTTP call returns. → That's today's behaviour too. The panel now ends in "Cancelled." instead of being stuck, and making the HTTP call itself cancellable is out of scope.
