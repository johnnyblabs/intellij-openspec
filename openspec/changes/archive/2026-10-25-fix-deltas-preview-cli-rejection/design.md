# Design

## Context

`OpenSpecToolWindowPanel.renderChangeDeltasPreview` runs off the EDT on the preview alarm thread. It calls `CliRunner.run(project, ChangeDeltaModel.changeShowArgs(name))` and has three ways to fail, all leading to `applyCliUnavailable`:
- a non-zero exit;
- any exception from parsing;
- any `CliException`.

The workflow panel solved the same problem in v0.14 through `CliJson.read`, the shared CLI response reader. It returns `Ok`, or `Failed` with `ENVELOPE` (the CLI's error `status` entry, with `message` and `fix`) or `UNREADABLE` (no usable JSON; the message is the ANSI-stripped stderr, or "exited with code N and no readable output").

`CliRunner.run` throws `CliException` in two cases:
- **It timed out:** "OpenSpec CLI command timed out after Ns". This change marks it with `timedOut()`.
- **Anything else:** for example, the process couldn't start. The original exception is kept as the cause.

Captured output (`fixtures/cli/1.15.0/errors/show-change-*`, `fixtures/cli/1.3.1/errors/show-change-*`):
- **1.15.0, missing change:** exit 1, envelope `Change "nosuch" not found at …/proposal.md`.
- **1.15.0, no proposal yet:** exit 1, envelope `… has no proposal.md yet. Run "openspec status --change …" …`.
- **1.3.1:** exit 1, empty stdout. Stderr is `Warning: Ignoring flags not applicable to change: scenarios` followed by `✖ Error: Change "nosuch" not found at …`.
- **On success** too, 1.3.1 and 1.5.0 write that warning to stderr. That's why the success path reads stdout only.

## Goals / Non-Goals

**Goals:**

- Show "requires the CLI" only when the CLI can't be run.
- When it ran and said no, show its reason in words the user can act on.

**Non-Goals:**

- Changing the success path, the deltas cache, or the workflow panel's handling.
- Handling a malformed `.openspec.yaml`. `show` succeeds there on 1.15.0, as captured.

## Decisions

### D1. Classify through `CliJson.read`
- **`Ok`:** parse with `ChangeDeltaModel.parse` and render as today. If the plugin can't parse an `Ok` payload, that's an unreadable answer, so it shows the new placeholder with "The plugin couldn't read the OpenSpec CLI's answer." It never shows "install the CLI".
- **`Failed`, either kind:** the new **Can't read this change** placeholder, with the reason from D2.
- **`CliException` with `timedOut()`:** the new placeholder, with the exception's message as the reason. The CLI exists but didn't answer, and telling the user to install it would be wrong. `CliRunner` sets the flag only at its timeout throw site, through `CliException.timeout(…)`. An earlier draft inferred a timeout from "no cause", but the test audit found that a cause can also come from failures after the process started, such as an interrupted wait.
- **Any other `CliException`** (the process couldn't start, or the run failed in some other way): the existing CLI-unavailable placeholder.
- **A `RuntimeException` before the run** (building the command line): the CLI-unavailable placeholder, as before this change, so the pane never stays on the previous selection. `ProcessCanceledException` is rethrown.
- **No CLI detected:** the existing placeholder, unchanged.

*Alternative rejected:* checking `exitCode` and grepping stdout. `CliJson.read` is the one reader every caller is supposed to use, and it already handles text before the JSON and the envelope shape.

### D2. Reason text
- **`ENVELOPE`:** `Failed.describe()`, the message plus the fix. This is the same wording as the workflow panel.
- **`UNREADABLE`:** clean the stderr-derived message:
  - drop lines starting with `Warning: Ignoring flags not applicable`;
  - strip a leading `✖ Error:` or `Error:` from each line;
  - drop empty lines and join what's left with spaces.

  If nothing is left, use `Failed.message()` as is. The cleanup is a small static helper on `SpecPreviewRenderer`, so it can be tested directly.
- **The renderer escapes the reason as HTML.** CLI text contains quotes and paths.

### D3. The placeholder
- **Function:** `SpecPreviewRenderer.cliRejectedPlaceholder(changeName, reason)`.
- **Content:**
  - the pane's "Change Deltas" header;
  - a `<div class="openspec-preview-cli-rejected">` marker, as the constant `CLI_REJECTED_MARKER`;
  - the text "**Can't read this change.** The OpenSpec CLI said: <reason>";
  - one line saying to fix the change and select it again.
- **It reuses `withOpenLink`**, so a selected artifact still gets its open-in-editor link.
- **Styling:** `paneCss()` styles the marker like the CLI-unavailable one.
- **Accessible name:** `PREVIEW_CLI_REJECTED_NAME = "OpenSpec preview: CLI could not read the change"`.

### D4. Never cache a rejection
- Only a successful render goes into `changeDeltasCache`, as today.
- A rejection is applied directly, so writing the missing proposal and reselecting the change asks the CLI again. This also covers the VFS-driven refresh, which clears the cache anyway.

## Risks / Trade-offs

- **Stderr wording varies by CLI version.** The cleaner removes only the known warning and the error prefix, and falls back to the raw message. The worst case is a slightly noisy reason, never a misleading "install the CLI". The 1.3.1 contract test pins today's form.
- **Timeout vs failed start.** The explicit `timedOut()` flag replaces any inference. A real-process test (`CliRunnerTimeoutTest`, macOS and Linux) pins that a hung CLI is flagged and a missing one isn't.
