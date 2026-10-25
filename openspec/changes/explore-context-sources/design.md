# Design

## Context

`ExploreContextService.assembleContext()` builds the context with `java.nio` reads only. It's called from `ExplorePromptService.build`, which `ExploreContextAction` calls on the EDT for all three deliveries (clipboard, editor tab, Direct API). Only the Direct API request itself runs in a `Task.Backgroundable` (`newExploreTask`).

Upstream facts, checked against the CLI's tagged sources and confirmed with the 1.15.0 captures under `src/test/resources/fixtures/cli/1.15.0/explore-context/`:

- **`status --change <n> --json`:** gains `artifactPaths.<id>.{outputPath, resolvedOutputPath, existingOutputPaths}` in **1.4.0**. On 1.3.x it has only `artifacts[]`.
  - **Paths:** `existingOutputPaths` are absolute realpaths (a symlinked `/tmp` comes back as `/private/tmp`). They're de-duplicated and string-sorted, and they're files only.
  - **Exclusions:** the glob matching excludes dot-files and dot-folders.
  - **Scope:** since 1.8 the paths stay inside the change dir.
  - **Key order:** keys follow `schema.yaml` declaration order. That differs from `artifacts[]`, which is in build order.
- **`list --specs --json`:** `specs[].id` exists from **1.5.0**.
  - **Nested ids:** from **1.7.0**, each id is the folder path of a real `spec.md`, so nested ids like `identity/user-auth` appear. Dot-folders are skipped, a symlinked folder isn't followed, a `spec.md` directly in `specs/` is ignored, and a parent and its child can both be listed.
  - **On 1.5–1.6:** it lists every top-level folder, including ones without a `spec.md`.
- **Store roots:** when the root is a store (`root.source` is `store`, `declared` or `global_default`, or `root.store_id` is set), both commands answer for that store, outside the IDE project. Supporting stores is out of scope.

## Goals / Non-Goals

**Goals:**

- Include every artifact file of every active change, for any schema and at any depth, named so the model can tell them apart.
- List every capability by its full id, nested ones included.
- Use OpenSpec's own answers where the CLI gives them, and fall back to a walk that mirrors upstream's rules where it doesn't.
- Never run context assembly, and so never a CLI call, on the EDT.

**Non-Goals:**

- Store-rooted projects (tracked separately). The context stays project-local and never reads files outside the project.
- Bounding or trimming context size (the Explore-as-a-conversation work).
- Changing how active changes are discovered (`ChangeService` stays the source).

## Decisions

### D1. Change artifacts: trust `artifactPaths`, by presence, not by version

- **Run:** for each active change, run `status --change <n> --json` through a CLI seam on `ExploreContextService`. The seam is the same shape as `ArtifactOrchestrationService.StatusCli`.
- **Parse:** `artifactPaths` goes onto `ChangeArtifactDag` as a `LinkedHashMap<String, ArtifactPaths>`. Gson preserves JSON key order in a `LinkedHashMap`, so schema order is kept.
- **When to trust it:** use the block when it's present and has at least one key; anything else falls back. Presence matches upstream exactly (1.4.0+), with no version table to keep, and it handles a CLI that doesn't answer (missing, timed out, error envelope) the same way.
- **Labels:**
  - A non-glob artifact (an `outputPath` without `*`) is labelled with its id, as today (`proposal`).
  - A glob artifact's files are labelled `<id> (<path inside the change>)`, such as `specs (specs/identity/user-auth/spec.md)` or `research (research/vendors/okta.md)`.
  - The path comes from `changeDir.toRealPath().relativize(file.toRealPath())`, with `\` replaced by `/`. Comparing realpaths on both sides absorbs the `/tmp` → `/private/tmp` difference.
- **Not trusted: paths outside the change.** If a path isn't inside the change dir's realpath, it's dropped. Combined with the store check (D4), this means a store-rooted answer, or a 1.4–1.7 `../` glob, can never pull outside files into a prompt.
- *Alternative rejected:* gating on `CliVersion.atLeast(.., "1.4.0")`. It's redundant with presence and still needs the fallback for a CLI that fails.

### D2. Change artifacts fallback: a recursive Markdown walk

- **What it includes:** without a usable `artifactPaths`, the fallback includes every `*.md` file under the change dir, at any depth.
- **Order:** spec-driven's `proposal.md`, `design.md` and `tasks.md` come first, so today's ordering is kept for the common case. The rest follow in sorted path order.
- **Exclusions:** a name starting with `.` skips that file or directory, which matches upstream's `dot:false`.
- **Labels:** each file is labelled with its path inside the change.
- **Symlinked directories:** not followed (`Files.walk` without `FOLLOW_LINKS`).
- **Why Markdown only:** every built-in artifact and template is Markdown, and it keeps binary or lock files out of a prompt.

### D3. Main specs: `list --specs --json` from 1.7.0, otherwise an upstream-shaped walk

- **When to use the CLI:** only when the detected CLI is at least 1.7.0, because below that ids can name folders without a spec. Here a version gate is right, not presence: the 1.5–1.6 shape is present but means something different. The gate is a CLI-capability constant, `LIST_SPECS_FLOOR = "1.7.0"`, kept outside `VersionSupport` like `DRY_RUN_FLOOR`.
- **Reading each spec:** each id's `openspec/specs/<id>/spec.md` is read for requirement summaries, as today. An id whose file can't be read is skipped.
- **The walk** (no CLI, older CLI, failure) mirrors `discoverSpecFiles`:
  - a recursive walk for files named `spec.md`;
  - the id is the parent folder's path relative to `openspec/specs/`, with `/` separators;
  - dot-folders are skipped and symlinked folders aren't followed;
  - a `spec.md` directly in `openspec/specs/` is ignored;
  - ids are sorted.
- **Headings:** each capability's heading is its full id (`### identity/user-auth`).

### D4. Store-rooted answers are ignored

- **The check:** if `root.source` is `store`, `declared` or `global_default`, or `root.store_id` is set, the CLI answered for a store. Today this applies to `list --specs --json`, and to `status` when its payload carries `root`.
- **What happens:** the context ignores that answer and uses the project walk. This keeps the out-of-scope case behaving as it does today, a project-local context, rather than mixing in another root's files.

### D5. Build the prompt in one background task for every delivery

- **The task:** `ExploreContextAction` gets a `newBuildTask(project, topic, delivery, …)` factory next to `newExploreTask`. It's a non-modal, cancellable `Task.Backgroundable`.
  - **`run()`:** calls `promptService.build(topic, delivery)` on the pooled thread.
  - **`onSuccess()` (EDT):** does the clipboard copy, the editor tab or the no-provider notice.
  - **Direct API:** the build moves into the existing `newExploreTask.run()` before `request.send`, so it stays one task with one cancel. Build failures reach the existing `RuntimeException` → `showError` path, and `setInstructionsSource` is applied through `invokeLater`.
  - **Cancellation:** `ExploreContextService` calls `ProgressManager.checkCanceled()` before each CLI call. `CliRunner` doesn't watch the indicator, so a cancel lands at the next check.
- **The Explore tab:** `showLoading` is called before the task starts, so "Thinking…" covers assembly. Every panel-originated path starts a task whose `onFinished` calls `restoreInput()`, including the no-provider fallback.
  - **Existing leak:** `runExploreDirect` returns early, without re-enabling the input, when the prompt service is missing. It now restores the input.
- **No read action:** the CLI subprocess must not hold the read lock, and a non-blocking read action would restart it on every write. The services the context uses already wrap their own VFS reads off the EDT (`ChangeService.getChangesFromDir`, `ConfigService.reload`).
  - `AiToolDetectionService.detectedTools` becomes `volatile`, since it's now written from a pooled thread.
- **No EDT assertion:** in unit-test mode `ProgressManager.run` executes a `Backgroundable` synchronously on the EDT, so an assertion in production would break every test. Tests check off-EDT behaviour by running `run()` on a pooled thread instead.
- *Alternatives rejected:*
  - `ReadAction.nonBlocking`: read lock plus restarts, as above.
  - Modal progress (`runWithModalProgressBlocking`): a Kotlin coroutine API, awkward from Java and not a stable API on 242, and a modal for a clipboard copy is poor UX.

## Risks / Trade-offs

- **More CLI calls per Explore:** one `status` per active change plus one `list --specs`. Each is a fast local process, but on a project with many active changes this adds latency. → It runs in the background, it's cancellable between calls, and the tab shows "Thinking…". Bounding context is separate work.
- **Larger prompts:** custom-schema and nested artifacts were silently missing before, so prompts can grow. → This is the intended fix. Size limits belong to the conversation work.
- **Cancel isn't instant:** a cancel waits for an in-flight CLI call. → Accepted; `CliRunner` timeouts bound it.
- **Fallback walk vs. upstream drift:** the walks mirror today's upstream rules, which could change. → Contract tests pin the CLI path to captured output. The walks are pinned by tests that state the upstream rule they mirror, and re-capture on the next CLI bump catches drift.
- **Async delivery changes timing for tests:** existing tests that call `runExplore*` and expect a synchronous clipboard copy must drive the task (call `run` and `onSuccess`, then pump the event queue).
