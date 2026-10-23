# Proposal

## Why

In a live test-drive against the real Claude API, four defects showed up, and all four affect users on the released plugin:

- **Claude keys that aren't scoped to a workspace can't be used at all.** Since Anthropic introduced personal and service-account keys, such a key must send `anthropic-workspace-id` on every request. Without it the API answers 400, and the plugin has no way to send the header.
- **Claude generation fails on models that think by default.** Their replies start with a `thinking` block that has no text, and the parser reads `content[0].text`. After Test, the model selector defaults to the first model in the live list, which is one of these models, so this is the path a new user follows.
- **Direct API can't generate the `specs` artifact, for any provider.** Its output path is the glob `specs/**/*.md`: one delta spec per capability, at `specs/<capability-path>/spec.md`. Both Direct API write paths use the glob as a literal file name, so generation fails with "Invalid file name: 'specs/**/*.md'" after the provider has already answered. Upstream's instructions also assume an agent that can run `openspec list --specs` and write files itself, which an API call can't. A related gap: once specs exist, a glob-valued dependency is passed to the next prompt (for example `tasks`) only as its path, so generated tasks never see the specs.
- **The Manage AI Tools dialog never reloads after an Add or Generate.** It refreshes the VFS on a background thread, the platform rejects that, and the reload never runs. The dialog keeps telling the user the tool "needs OpenSpec skills" when the skills were in fact generated.

This is a patch release for these four defects. Each one is tracked in the linked tracker entries.

## What Changes

- **Claude Workspace ID setting.** Add an optional Workspace ID field to the Direct API settings, shown only for the Claude provider and validated against the `wrkspc_` prefix. When set, it is sent as `anthropic-workspace-id` on both the list-models and messages requests, so Test exercises it.
- **Actionable Claude workspace errors.** The 400 "not scoped to a workspace" error says what to do: enter a Workspace ID, or use a key scoped to one workspace. A 404 for an unknown workspace says the ID wasn't found or the key can't access it.
- **Claude auth header.** Claude requests authenticate with `Authorization: Bearer <key>` instead of the legacy `x-api-key`. Both are accepted today, and Bearer is the documented form, verified live on list-models and messages.
- **Claude response parsing.**
  - Generated text is the concatenation of every `type: "text"` content block. `thinking` and `redacted_thinking` blocks are skipped.
  - A reply with no text block raises a clear error that names the response's `stop_reason` (for example, the token limit was reached while thinking). It no longer fails with a null-pointer error.
- **Multi-file artifacts over Direct API.**
  - For an artifact whose output path is a glob, the Direct API prompt adds an output-format section. It asks for every file to be preceded by a marker line naming its path relative to the change, and it gives the model what it can't look up itself: the existing capability paths under `openspec/specs/`, the full text of the main specs the proposal names (needed for MODIFIED deltas), and which spec files the change already has.
  - The plugin parses the response into files, validates every path against the artifact's glob (relative, no `..`, matching the pattern), and writes them all, or none if any path is invalid. A response with no marked files is a clear error, and nothing is written.
  - Both write paths (Generate in the tool window, and Generate All / Fast-Forward) use the same writer.
- **Glob-valued dependencies are inlined.** When a completed dependency's path is a glob, the generation prompt inlines every existing file in the change that matches it, each labeled with its path, instead of only the path. This applies to every delivery method, so a copied prompt carries the specs too.
- **Manage AI Tools refresh.** After the CLI succeeds, the dialog refreshes the project directory asynchronously, with the dialog's modality, then reloads the tool list and shows the success notification on the EDT. Any failure, not only a CLI failure, resets the button and reports the error.
- **Contract fixtures.** The doc-sourced Claude fixtures are replaced with live-captured, sanitized responses: list-models (200), the unscoped-key and invalid-workspace 400s, an unrelated 400, a text-only message, a thinking-first message, and a multi-file `specs` response to the plugin's real prompt.

Out of scope, scheduled for the next minor: OpenAI Organization/Project fields, the fallback model-list refresh, error-hint and retry improvements, and the Gemini parser hardening.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `ai-integration`:
  - The "AI providers" requirement changes the Claude auth header to Bearer, adds the optional Claude workspace selector, and requires the response parser to extract text blocks while skipping thinking blocks.
  - A new requirement covers Claude workspace errors.
- `ai-integration` also gains a requirement for multi-file artifacts generated over Direct API.
- `workflow`: the "Generation prompt tolerates glob-valued dependency paths" requirement changes from a path-only reference to inlining the matching files.
- `edt-compliance`: the "VFS refresh threading" requirement gains a rule that a background task SHALL NOT call a synchronous VFS refresh, and that dependent UI updates run after an asynchronous refresh completes.

## Impact

- **Code:**
  - `ai/DirectApiService.java`: request builders, the Claude parser, and the error mapping for the workspace 400 and 404.
  - `settings/OpenSpecSettings.java`: a new persisted, non-secret `aiWorkspaceId` field.
  - `settings/OpenSpecSettingsPanel.java`: the Workspace ID field, its visibility per provider, its validation, and using it in Test.
  - `dialogs/ManageAiToolsDialog.java`: the refresh and the error handling.
  - A new multi-file response format (prompt section and parser), `model/ArtifactInstruction.java` (glob dependency inlining), `services/ArtifactOrchestrationService.java` (the shared writer), and `toolwindow/WorkflowActionPanel.java` (Generate uses that writer).
- **Tests and fixtures:**
  - `DirectApiResponseContractTest`, `DirectApiModelsContractTest` and `DirectApiServiceTest`, plus the fixture README provenance table.
  - A test for the dialog's refresh path.
- **Compatibility:**
  - The new setting is optional and empty by default, so existing users with workspace-scoped or legacy keys are unaffected.
  - One new IntelliJ Platform API reference: `RefreshQueue.refresh(..., ModalityState, files)`, public and non-deprecated from 2024.2 through 2026.x.
  - Copied prompts for artifacts that depend on `specs` get longer, because they now include the specs.
  - Supported range unchanged: IntelliJ IDEA 2024.2 and later.
- **Docs:** the Direct API section of the user-facing feature reference and setup docs, and the CHANGELOG.
