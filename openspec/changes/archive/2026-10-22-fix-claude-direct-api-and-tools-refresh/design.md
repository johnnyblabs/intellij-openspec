# Design

## Context

See proposal.md, "Why", for the three defects. The current state that shapes the fix:

- **Request builders are already pure and static.** `DirectApiService.buildClaudeRequest(model, key, prompt)` and `buildListModelsRequest(provider, key)` are package-private and unit-tested on their headers. Generation (`generate`/`generateRaw`/`testConnection`) reads the provider and model from `OpenSpecSettings` (project-level state). Settings → Test calls `listModels(provider, key)` with the **unsaved** key from the form.
- **Error handling is generic.** `buildApiError` logs the body and builds `"<Provider> API error: <error.message>"` with a hint chosen by status code. The Test handler maps only 401 and 403.
- **The Claude parser is fragile.** `parseClaudeResponse` reads `content[0].text`.
- **Live-captured responses exist.** They are sanitized and staged for commit: list-models 200 (14 models), the unscoped-key 400, a text-only message (`claude-sonnet-5`), and a thinking-first message (`claude-sonnet-5-5`, `[thinking(empty), text]`). The current Claude fixtures are doc-sourced.
- **The dialog bug.** `ManageAiToolsDialog.runCliAction` runs the CLI in a `Task.Backgroundable` and then calls `VirtualFileManager.syncRefresh()` on that pooled thread. That throws "Access is allowed from write thread only". The `catch` handles only `CliRunner.CliException`, so the reload and the button reset never run.
- **A related pattern exists.** `OpenSpecBaseAction` (around lines 133-144) refreshes off-EDT with `LocalFileSystem.refreshFiles(List.of(projectDir), true, true, postRunnable)`. It is not modal-aware, so this modal dialog can't reuse it as is (decision 6).

## Goals / Non-Goals

**Goals:**
- Keep the request builders pure, so the workspace and auth headers can be asserted without a network or an Application.
- Ground every response-shape assertion in a live-captured fixture.
- Make the smallest correct fix to the dialog, following the existing modality pattern (`BulkArchiveDialog`).

**Non-Goals:**
- A workspace picker populated from the Admin API's List Workspaces. It's unverified that a non-admin key can call it; the text field is the dependable baseline and the picker can come later.
- Making Test exercise generation, or making it match the wizard's check. That's tracked separately for the next minor.
- Any change to OpenAI or Gemini request handling (the multi-file format is provider-neutral and applies to all three).
- Streaming files to disk as they arrive.

## Decisions

### 1. Workspace ID is a project-level plain setting, alongside provider and model
Add `aiWorkspaceId` (default `""`) to `OpenSpecSettings.State`, with a getter and setter. It isn't a secret, so it stays out of PasswordSafe. It's plugin state, so it never goes in `openspec/config.yaml`.
- *Alternative:* application-level storage, matching the app-wide key in PasswordSafe. Rejected for this patch: `aiProvider` and `aiModel` are project-level, and splitting the Claude configuration across two scopes would be surprising. The cost is re-entering the ID per project. The actionable 400 message (decision 4) makes that self-explanatory.

### 2. The builders take the workspace as a nullable parameter; callers resolve it
- `buildClaudeRequest(model, key, prompt, @Nullable workspaceId)` and `buildListModelsRequest(provider, key, @Nullable workspaceId)` add `anthropic-workspace-id` only when the value isn't blank, and only for Claude.
- Generation reads the value from settings. `listModels` gains a workspace argument, so Settings → Test passes the **form's current value**, the same way it already passes the unsaved key.
- *Alternative:* builders read the settings directly. Rejected: that breaks the pure, static testability and would make Test ignore an unsaved edit.

### 3. Bearer replaces x-api-key for Claude
Both builders send `Authorization: Bearer <key>` and stop sending `x-api-key`. Verified live on 2026-10-22: list-models and messages both return 200 with Bearer plus the workspace header. Anthropic's auth docs call `x-api-key` legacy but still supported, so this is a cleanup with no compatibility risk.

### 4. Map the workspace errors by error type and message, with the raw message as fallback
In the Claude error path, map:
- 400 with `error.type == "invalid_request_error"` and a message mentioning `anthropic-workspace-id` → *"This Claude API key isn't scoped to a workspace. Enter a Workspace ID in Settings → Tools → OpenSpec → Direct API, or use a key scoped to one workspace."*
- 400 with the same type whose message says the header "must be a valid workspace ID" → *"Claude doesn't recognize this Workspace ID. Copy it from Console → Settings → Workspaces …"* This one also names the header, so it is checked first. It was found by a live capture during implementation: a mistyped ID returns it, not a 404.
- 404 with `error.type == "not_found_error"` and a message starting `Workspace` → *"Workspace ID not found, or this key can't access it."*

Anything else keeps today's behaviour. The Test handler shows the mapped message rather than mapping only 401 and 403.
- *Risk:* the match depends on provider wording. A contract test against the **live-captured** 400 body pins it. If the wording changes, the user falls back to Anthropic's own message, which is still accurate.

### 5. The Claude parser concatenates text blocks and names the stop reason when there are none
Iterate `content`, append each block whose `type` is `"text"`, and skip every other type (`thinking`, `redacted_thinking`, anything future). The blocks are joined with no separator, because they are consecutive pieces of one answer. If no text block is found, throw `AiApiException("Claude returned no text (stop_reason: <value>)")`, with a token-limit hint when the stop reason is `max_tokens`.
- *Alternative:* turn thinking off in the request. Rejected: Claude Sonnet 5.5 rejects `thinking: {type: "disabled"}`, and other models only accept it at some effort levels, so per-model request shaping would be fragile. Tolerating thinking blocks works on every model.

### 6. The dialog refreshes asynchronously with its own modality, and catches everything
After a successful CLI run:
1. Resolve the project directory from `project.getBasePath()`.
2. Call a small static helper, `refreshThenPost(dir, dialogModality, expired, post)`. It calls `RefreshQueue.getInstance().refresh(true, true, onFinish, dialogModality, List.of(dir))`, and `onFinish` posts `post` (`loadTools()` plus the notification) with `invokeLater(post, dialogModality, expired)`.
3. If the directory can't be resolved, post the reload directly.

The dialog's modality is captured once after `init()`, as `BulkArchiveDialog` does. Every EDT hop in the task uses it; the old one-argument `SwingUtilities.invokeLater` calls are gone. The `catch` widens to `Exception` (keeping the `CliException` message path), so any failure resets the button and reports the error. A `ProcessCanceledException` also resets the button and is then rethrown. `addToolBuiltIn` runs on the EDT from a button handler, where `syncRefresh()` is legal; it is left as is and noted in a comment.
- *Why not `LocalFileSystem.refreshFiles(..., postRunnable)`, as `OpenSpecBaseAction` does:* it uses the calling thread's default modality. On a `Task.Backgroundable` that is non-modal, so in this **modal** dialog the refresh, and the reload chained after it, would be held until the dialog closes. A negative-control test pins this.
- *Alternative:* `invokeLater(syncRefresh)`. That works, but it blocks the EDT on a global refresh.

### 7. Multi-file artifacts use a marker line per file, parsed and validated before any write
For an artifact whose output path is a glob, the Direct API prompt (not the copied prompt, which is for an agent that writes its own files) gets an extra section:
- **The format.** Each file starts with a line of its own, `<!-- file: <path relative to the change> -->`, followed by the file's full content. Nothing goes outside a file. An HTML comment is valid Markdown, so a marker that ends up in a file by mistake is harmless.
- **What the model can't look up.** Upstream's instructions tell an agent to run `openspec list --specs` and to skip files that already exist. The plugin supplies those facts instead: the capability paths under `openspec/specs/` (directories holding a `spec.md`), the full text of each main spec whose capability path the proposal names (MODIFIED deltas must copy whole requirement blocks), and the paths of the artifact's files already in the change, with an instruction to write only the missing capabilities.

The parser splits the response on marker lines. It drops a code fence that wraps a whole file, and trims blank lines at the ends. Every path must be relative, use `/`, contain no `..` segment, and match the glob, where `**` matches across directories and `*` within one. Validation runs before writing: one bad path rejects the whole response and nothing is written. No markers is an error too; the plugin never guesses a file name.
- *Alternative:* a JSON envelope (`[{path, content}]`). Rejected: escaping long Markdown in JSON is where models make mistakes, and a truncated response loses everything instead of the files before the cut.
- *Alternative:* one API call per capability. Rejected for the patch: it needs the plugin to parse the proposal's capability list reliably, and it multiplies cost.

### 8. One writer for both Direct API paths
`ArtifactOrchestrationService.writeArtifactResult(instruction, content)` handles both a single path and a glob (via decision 7), and returns the files it wrote. Generate in the tool window called its own inline write; it now calls this method on its background thread and posts only the notification and refresh to the EDT.

### 9. Glob-valued dependencies inline their matching files
`ArtifactInstruction` resolves a glob dependency against the change directory with the same matcher as decision 7. Each matching file is inlined under its relative path, in path order. With no match, it keeps today's path-only reference. A glob is still never turned into a single `Path`, so the Windows behaviour behind the original requirement is preserved.

## Risks / Trade-offs

- **[Risk] The live fixtures could leak identifiers.** → They were sanitized at capture (request, message and workspace IDs and thinking signatures replaced) and grepped for `sk-ant` and `wrkspc_` IDs before staging. The pre-push leak guard still applies.
- **[Risk] An existing user's model choice now behaves differently.** → The parser change only affects responses that start with non-text blocks, which failed before. Text-only responses parse exactly as before (pinned by the text-only fixture).
- **[Risk] The model ignores the marker format.** → Nothing is written and the error says the response named no files, so the user can retry or copy the prompt instead. The format is pinned by a contract test against a live response to the plugin's real prompt.
- **[Trade-off] Bigger prompts.** Inlined main specs and dependency specs make prompts longer. The main specs are limited to the ones the proposal names.
- **[Trade-off] The Workspace ID is per project.** → Accepted for the patch (decision 1); revisit if users ask.
- **[Risk] The dialog refresh behaves differently on 2024.2.** → `RefreshQueue.refresh(boolean, boolean, Runnable, ModalityState, Collection)` is public and non-deprecated from 2024.2 through 2026.1, and verifyPlugin runs in pre-push and CI.

## Migration Plan

No migration. The new setting defaults to empty, which reproduces today's request exactly (minus the auth-header rename). Rollback is a normal revert.
