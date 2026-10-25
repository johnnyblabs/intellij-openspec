# Proposal

## Why

The Explore tab's input can stay disabled until the tool window is rebuilt. Tracked by the linked tracker entry.

- **The fallback leaves the panel stuck.** Sending a topic disables the input. When the Direct API provider is no longer configured (the key was removed, or the provider set to none, after the tab was created), `ExploreContextAction.deliverDirectApi` copies the prompt to the clipboard and returns. Nothing re-enables the panel or tells it what happened.
- **Two balloons on that path.** The fallback calls `deliverClipboard`, which shows "Explore prompt copied…", then shows a second "No AI provider configured…" balloon.
- **Other failure paths do the same.** The background request only catches `AiApiException`:
  - a `RuntimeException` from the provider code leaves the panel at "Thinking..." with the input disabled;
  - cancelling the request from the progress indicator leaves it there too.

## What Changes

- **Fallback, shown in the panel.** When the provider isn't configured and the Explore panel exists, the plugin copies the prompt, re-enables the input, and shows the reason in the panel: no Direct API provider is configured, the prompt was copied to the clipboard, and Settings is where to configure one. It shows no balloon. Without a panel, there's one balloon, not two.
- **No failure path leaves the input disabled.**
  - A `RuntimeException` from the request shows as an error in the panel. `ProcessCanceledException` is still rethrown.
  - A cancelled request shows "Cancelled" in the panel.
  - The task's `onFinished` re-enables the input whatever happened, so it acts as the `finally`.
- **Clipboard through the platform.** The copy uses `CopyPasteManager` instead of AWT's system clipboard. That's the platform's way, and it works in headless tests.
- **Unchanged:** the success and `AiApiException` paths, the loading state, and the non-Direct-API delivery modes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `explore-thinking-space`: "Loading and error states" gains scenarios for the clipboard fallback, an unexpected failure, and a cancelled request, each leaving the input enabled.

## Impact

- **Code:**
  - `actions/ExploreContextAction`: the fallback branch, the background task's catch, `onCancel` and `onFinished`, the clipboard call, and a `@TestOnly` seam for the raw request.
  - `toolwindow/ExplorePanel`: a notice state for the fallback, a cancelled state, and `@TestOnly` accessors.
- **Tests:** light platform tests that drive the real `ExplorePanel` through its Send button and the real `ExploreContextAction` path. Each must fail against today's code:
  - the provider is unconfigured after the panel was created;
  - the request throws a `RuntimeException`;
  - the request is cancelled.
- **Platform:** `CopyPasteManager` and `Task.Backgroundable.onCancel`/`onFinished` are long-standing public API on 2024.2 and later. The plugin still supports IntelliJ IDEA 2024.2 and later. `verifyPlugin` runs because new `com.intellij.*` references are added.
- **Docs:** a CHANGELOG `## Unreleased` Fixed entry.
