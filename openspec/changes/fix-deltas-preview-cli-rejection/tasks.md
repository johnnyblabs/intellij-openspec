# Tasks

## 1. Fixtures

- [x] 1.1 Add `show-change-missing` and `show-change-noproposal` to `scripts/capture-error-fixtures.py`, with the preview's exact args (`show <name> --type change --json`). Commit the captures for 1.15.0 (`fixtures/cli/1.15.0/errors/`) and 1.3.1 (`fixtures/cli/1.3.1/errors/`, captured through `OPENSPEC_BIN="npx -y @fission-ai/openspec@1.3.1"`). Note the recipe in the fixtures README. Verify: the 1.15.0 stdout files hold error envelopes, the 1.3.1 stderr files hold the `✖ Error:` line after the "Ignoring flags" warning, and no machine paths remain.

## 2. Renderer

- [x] 2.1 Add `SpecPreviewRenderer.cliRejectedPlaceholder(changeName, reason)` with `CLI_REJECTED_MARKER`, the "Change Deltas" header, the "Can't read this change" text with the reason HTML-escaped, and the fix-and-reselect line. Style the marker in `paneCss()`. Verify with renderer tests:
  - the header names the change;
  - the marker is present and differs from `CLI_UNAVAILABLE_MARKER`;
  - a reason with `<`, `&` and quotes comes out escaped;
  - the text never says "Install";
  - `withOpenLink` puts the link after the header.
- [x] 2.2 Add the reason cleaner (design D2). Verify on the captured 1.3.1 stderr: the warning is gone, the `✖ Error:` prefix is stripped, and the not-found sentence remains. Verify that an input with nothing left falls back to the raw message, and that envelope reasons use `Failed.describe()`.

## 3. Classification

- [x] 3.1 Route `renderChangeDeltasPreview` through `CliJson.read`, following design D1: `Ok` → render; `Failed` → the rejected placeholder; a `CliException` with `timedOut()` (set only by `CliRunner`'s timeout) → rejected; any other `CliException` or a `RuntimeException` before the run → unavailable; an `Ok` payload the plugin can't parse → rejected. A panel test with a script CLI (`ChangeDeltasRejectionPanelTest`) checks the panel shows the rejection and doesn't cache it, and `CliRunnerTimeoutTest` pins the timeout flag on a real process. Extract the classification as a static, testable function returning the fragment or kind, so it doesn't need a tool window. Verify with a contract test that feeds the captured `.stdout`, `.stderr` and `.exit` as a `CliResult`:
  - 1.15.0 missing and no-proposal give the rejected placeholder with the CLI's message;
  - 1.3.1 missing gives the rejected placeholder with the cleaned reason;
  - a captured successful show still renders deltas;
  - an `IOException`-caused `CliException` gives the unavailable placeholder;
  - a cause-less timeout gives the rejected placeholder.
- [x] 3.2 Apply a rejection without caching it, and give it the accessible name `PREVIEW_CLI_REJECTED_NAME` (design D3–D4). The rule lives on the outcome (`ChangeShowOutcome.cacheable()`, true only for deltas), and the panel caches only when it holds, so it can be tested without a tool window. Verify: `cacheable()` is false for the rejected and unavailable outcomes and true for deltas, and the rejected accessible name differs from the CLI-unavailable one.

## 4. Docs and verification

- [x] 4.1 Add a `### Fixed` line for users under `## Unreleased` in `CHANGELOG.md`. Update the Browse preview row in `docs/feature-reference.md` to mention the new placeholder. Verify: `DocumentationHygieneTest` passes.
- [x] 4.2 Mutation-check the tests. Each of these must fail at least one test, then be reverted:
  - route `Failed` to the unavailable placeholder;
  - drop the warning filter;
  - stop escaping the reason;
  - cache the rejection;
  - treat a timeout as unavailable.
- [x] 4.3 Run `./gradlew build`. Verify: it passes, including the coverage floor. Run `./gradlew verifyPlugin` only if the diff adds new `com.intellij.*` references.

## Workflow follow-up

- Demo: rename a change directory in the Project View and reselect it in Browse. It shows "Can't read this change" with the CLI's reason. A change created with no proposal shows the "has no proposal.md yet" reason.
