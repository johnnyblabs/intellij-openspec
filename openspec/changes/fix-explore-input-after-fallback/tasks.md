## 1. Panel

- [x] 1.1 Add `ExplorePanel.showNotice(topic, message)` (muted text, topic header, input re-enabled) and `restoreInput()`. Add the `@TestOnly` accessors: `sendButtonForTest()`, `isInputEnabledForTest()` and `responseTextForTest()`. Move Copy Response to `CopyPasteManager`.

## 2. Action

- [x] 2.1 Split `deliverClipboard` into `copyToClipboard(prompt)`, which uses `CopyPasteManager`, and its balloon. In `deliverDirectApi`'s unconfigured branch:
  - with a panel: copy, then `showNotice` with the fallback text, and no balloon;
  - without one: copy, then show one balloon.
- [x] 2.2 In the background task:
  - catch `RuntimeException` in `run()`: rethrow `ProcessCanceledException`, and log anything else at WARN and show it with `showError`;
  - add `onCancel()`, which shows "Cancelled.";
  - add `onFinished()`, which calls `restoreInput()`.
- [x] 2.3 Add the `@TestOnly` seams for the raw request and the configured check.

## 3. Tests

- [x] 3.1 Light platform tests that click the real Send button on a real `ExplorePanel` registered with `ExplorePanelService`, then flush the EDT. Each must fail against today's code. Today's code has no test seams to compile against, so this is checked by mutations that put back today's behaviour, such as returning from the fallback without the panel notice; see 3.2:
  - **Unconfigured provider:** the input is enabled, the response text holds the fallback message, the clipboard holds the prompt, and no balloon was shown, through a notification-bus listener.
  - **A `RuntimeException` from the request:** the input is enabled and the response shows "Explore failed".
  - **A cancelled request:** the input is enabled and the response shows "Cancelled.".
  - **A provider error:** the input is enabled and the error is shown. This is a regression guard for today's path.
- [x] 3.2 Mutation-check by hand. Each must make a test fail:
  - remove the `onFinished` re-enable together with the `showNotice` re-enable;
  - catch only `AiApiException`;
  - show the fallback balloon when a panel exists;
  - swallow `ProcessCanceledException`. Assert it propagates through a direct `run()` call with a cancelled indicator.

## 4. Docs and verify

- [x] 4.1 A CHANGELOG `## Unreleased` Fixed entry.
- [x] 4.2 `./gradlew build` and `./gradlew verifyPlugin` are green, because of the new `com.intellij.*` references, and `openspec validate fix-explore-input-after-fallback --strict` is clean.
- [x] 4.3 Manual check in a sandbox IDE: skipped by maintainer decision. The real panel and action paths are covered by the light platform tests, which click the real Send button. They cover the unconfigured provider, an unexpected failure, a cancel, a provider error and success.

## Workflow follow-up

- Archive the change, then close the tracker entry.
