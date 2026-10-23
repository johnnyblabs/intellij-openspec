# Tasks

## 1. Live-captured Claude fixtures

- [x] 1.1 Commit the four sanitized live captures under `src/test/resources/fixtures/ai/claude/`:
  - `models-list-live.json`
  - `models-400-unscoped-key.json`
  - `messages-text-only.json`
  - `messages-thinking-first.json`

  Remove the doc-sourced `messages-response.json` and `models-list.json`. Verify with `grep -rnE "sk-ant|wrkspc_[A-Za-z0-9]{6}" src/test/resources/fixtures/ai/claude/`, which must return nothing.
- [x] 1.2 Update `src/test/resources/fixtures/ai/README.md`: mark the Claude rows live-captured (capture date, model, sanitization applied) and record the thinking-first shape. Verify that the table names every Claude fixture file that exists.

## 2. Claude response parsing (thinking blocks)

- [x] 2.1 Change `DirectApiService.parseClaudeResponse` to concatenate every `type == "text"` block and skip all other block types. When there is no text block, throw `AiApiException` naming `stop_reason`, with a token-limit hint for `max_tokens`. Verify that the existing Claude contract test, re-pointed at `messages-text-only.json`, still passes.
- [x] 2.2 Add contract tests in `DirectApiResponseContractTest`:
  - `messages-thinking-first.json` returns the text block's content;
  - a no-text-block body (derived from the thinking-first fixture with the text block removed) throws an `AiApiException` whose message contains its `stop_reason`.

  Verify that both fail against the old `content[0].text` parser (temporarily revert the parser locally) and pass with the fix.

## 3. Claude auth and workspace header

- [x] 3.1 Add `aiWorkspaceId` (default `""`) with a getter and setter to `OpenSpecSettings.State`, persisted with provider and model. Verify with a settings round-trip test, and confirm `OpenSpecSettingsMigrationTest` still passes.
- [x] 3.2 Make the request builders send `Authorization: Bearer <key>` (no `x-api-key`) and add `anthropic-workspace-id` only when the workspace is non-blank:
  - `buildClaudeRequest(model, key, prompt, workspaceId)`;
  - `buildListModelsRequest(provider, key, workspaceId)`, which sends the workspace header for Claude only.

  Thread the workspace through `generate`, `generateRaw`, `testConnection` (from settings) and `listModels(provider, key, workspaceId)`. Verify with `DirectApiServiceTest` header assertions: Bearer is present and `x-api-key` is absent; the workspace header is present when set and absent when blank or null; there's no workspace header for OpenAI or Gemini; the key is never in the URI.
- [x] 3.3 Map the Claude workspace errors in the error path: 400 `invalid_request_error` mentioning `anthropic-workspace-id`, and 404 `not_found_error` starting `Workspace`. Each gets the actionable message from design decision 4; other errors are unchanged. Verify with a test that feeds `models-400-unscoped-key.json` through the error builder and asserts the mapped message, plus a test that an unrelated 400 keeps the provider's message.
- [x] 3.4 Settings panel:
  - Add a "Workspace ID (optional)" field to the Direct API section, visible only when the provider is Claude, with help text: "Required for keys that aren't scoped to a workspace. Find it under Console → Settings → Workspaces."
  - Validate the `wrkspc_` prefix in `apply()`, raising a `ConfigurationException` with a clear message.
  - Pass the field's current value to `listModels` in Test.
  - Show the mapped error message in Test for any `AiApiException`, not only 401 and 403.

  Verify with a panel test: the field is shown or hidden as the provider changes, an invalid ID is rejected on apply, and a valid ID round-trips through `reset()` and `apply()`.
- [x] 3.5 Update the user docs for the Workspace ID field, when it's needed, and where to find the ID:
  - `docs/getting-started-api.md`;
  - `docs/direct-api-workflow.md`;
  - the "Direct API Providers" section of `docs/feature-reference.md`.

  Verify that the docs mention the field name exactly as the UI label, and that a leak grep of the changed docs is clean.

## 4. Manage AI Tools refresh

- [x] 4.1 In `ManageAiToolsDialog.runCliAction`, replace the off-EDT `VirtualFileManager.syncRefresh()` with a static helper, `refreshThenPost(dir, dialogModality, expired, post)`. It uses `RefreshQueue.refresh(true, true, onFinish, dialogModality, List.of(dir))` and then `invokeLater(post, dialogModality, expired)`, where `post` runs `loadTools()` and the success notification. Capture the dialog's modality after `init()`, and pass it on every EDT hop. If there's no project directory, post directly. Widen the catch to `Exception` so any failure resets the button and reports it, and rethrow `ProcessCanceledException`. Add a comment that `addToolBuiltIn`'s `syncRefresh()` is EDT-only. Verify by running the sandbox: Manage AI Tools → add Claude Code. The tool should move to CONFIGURED with no SEVERE "write thread" entry in `log_runIde/idea.log`.
- [x] 4.2 Add regression tests:
  - a structural guard, `ManageAiToolsRefreshHygieneTest`: no `syncRefresh()` in `runCliAction`, the reload goes through `refreshThenPost`, the catch is `Exception`, the refresh carries the modality, and every EDT hop passes one;
  - a modality test, `ManageAiToolsDialogModalityTest`: the helper, run off the EDT inside a simulated modal state, reloads before the modal is left; an expired dialog is not reloaded; and a negative control shows the `refreshFiles` shape is held.

  Verify that the guard fails against the old code.

## 5. Multi-file artifacts over Direct API

- [x] 5.1 Add a multi-file response format class (prompt section plus parser), as in design decision 7: marker lines, fence and blank-line trimming, glob matching (`**` across directories, `*` within one), path validation (relative, `/` only, no `..`), all-or-nothing validation, and an error when there are no markers. Verify with unit tests for each rule, including a glob-matcher table.
- [x] 5.2 Build the Direct API prompt section for glob outputs: the format, the capability paths under `openspec/specs/`, the full text of each main spec the proposal names, and the artifact's existing files in the change. Use it only for Direct API, never for copied prompts. Verify with a test on a temp project that the section lists the right paths and inlines only the named main spec.
- [x] 5.3 Capture a live Claude response to the plugin's real `specs` prompt for the demo change, sanitize it, commit it as `fixtures/ai/claude/messages-specs-multi-file.json` with README provenance, and add a contract test that parses it into the expected files. Verify that the test fails if the marker format changes.
- [x] 5.4 Make `ArtifactOrchestrationService.writeArtifactResult` handle glob output paths and return the written files, and switch the tool-window Generate's Direct API branch to it (background thread; only the notification and refresh on the EDT). Verify with a platform test that writing a parsed multi-file result creates `specs/<capability>/spec.md` files, and that a rejected response writes nothing; add a structural guard that `WorkflowActionPanel` no longer calls `createChildData` with the instruction's output path.
- [x] 5.5 Inline glob-valued dependencies in `ArtifactInstruction` (design decision 9). Verify with tests: matching files are inlined under their relative paths in order, no match keeps the path-only reference, and the existing Windows/glob tests still pass.
- [x] 5.6 Docs and CHANGELOG: note in `docs/direct-api-workflow.md` and the feature reference that `specs` can be generated over Direct API (one file per capability) and that later prompts include the specs; add a CHANGELOG Fixed entry. Verify that the leak grep is clean.

## 6. Integration and release notes

- [x] 6.1 Add user-facing entries under `## Unreleased` in `CHANGELOG.md`:
  - Claude keys that aren't scoped to a workspace work via the new Workspace ID setting;
  - generation no longer fails on Claude models that think by default;
  - Manage AI Tools now updates after adding a tool.

  Verify the wording is vendor-neutral and user-facing only.
- [x] 6.2 Run `./gradlew build` (tests, coverage floor) and `./gradlew verifyPlugin`, and verify both pass.
- [x] 6.3 Sandbox end to end:
  - with an unscoped key plus a Workspace ID, Test shows "Connected — N models";
  - Generate via API on a thinking-by-default model (for example `claude-sonnet-5-5`) writes the artifact;
  - clearing the Workspace ID reproduces the actionable 400 message;
  - Generate `specs` via API on the demo change writes one `specs/<capability>/spec.md` per capability, and a following `tasks` generation's prompt includes them;
  - Manage AI Tools → add a tool reloads the list while the dialog is open.

  Verify by checking for no Claude 400 in `log_runIde/idea.log` during the configured run.

## Workflow follow-up

- Archive the change, then cut v0.14.1 (`/release-cut 0.14.1`). The Direct API provider gate runs as part of the cut.
- The next minor keeps the remaining Direct API items (OpenAI fields, fallback refresh, error hints and retry, Gemini parser).
