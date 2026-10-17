## Context

See proposal.md, Why. The plugin targets 2024.2+ (`sinceBuild = "242"`, open-ended `untilBuild`) and compiles against IntelliJ IDEA Community 2024.2. `pluginVerification.ides` uses `recommended()`, which resolves to 2024.2, 2024.3, 2025.1 and 2025.2. JetBrains publishes no Community distribution from 2025.3 on, so `recommended()` never reached the IDEs where the new deprecations appear.

The Marketplace verifier reported four deprecated APIs on 2026.1, 2026.2 and the 2026.3 EAP. Each was checked against the platform jars on 2024.2 and current releases:

| API | Usages | Replacement at the 2024.2 floor | Verdict |
|---|---|---|---|
| `ReadAction.compute(ThrowableComputable)` | 5 | `Application.runReadAction(ThrowableComputable)`, not deprecated on 2024.2 through `master` | Migrate |
| `FileEditorManager.openFile(VirtualFile, boolean)` | 13 | `OpenFileDescriptor(project, file).navigate(true)`, not deprecated on 2024.2 through `master` | Migrate |
| `TerminalToolWindowManager.createShellWidget(...)` | 1 | The new terminal API does not exist on 2024.2 | Exception |
| Search Everywhere `getDataForItem` / contributor data API | 6 on EAP | Abstract on 2024.2 (must be overridden); its replacement does not exist on 2024.2 and is experimental and coroutine-only | Exception |

The removal canary in `verifyPlugin`'s `doLast` and the severity gate in `pluginVerification` are unchanged. Plain deprecations stay report-only, per the `ci` spec.

## Goals / Non-Goals

**Goals:**
- Zero plain-deprecated usages on the verified set other than the two documented exceptions.
- A verified set that includes the newest stable IntelliJ IDEA release, at a verify-job cost close to today's.
- A static guard so the two migrated calls cannot come back unnoticed. `build` compiles against 2024.2, where neither is deprecated, so only the guard and `verifyPlugin` would see a regression.

**Non-Goals:**
- Raising the 2024.2 floor, or migrating the terminal and Search Everywhere exceptions.
- Verifying against EAP builds in CI.
- Adding verification for other JetBrains IDEs (WebStorm, PyCharm and so on). The plugin depends only on `platform` and `yaml`.

## Decisions

### D1. `Application.runReadAction(ThrowableComputable)` replaces `ReadAction.compute`

On 2024.2, `ReadAction.compute(c)` is a one-line delegate to `ApplicationManager.getApplication().runReadAction(c)`. Calling the target directly keeps behaviour byte-for-byte, including the checked `IOException` from `VirtualFile.contentsToByteArray()` that two of the lambdas throw (the `ThrowableComputable<T, E>` overload propagates it).

*Alternatives considered:*
- `ReadAction.computeBlocking`: this is the newer platform's named replacement, but it does not exist on 2024.2.
- `ReadAction.nonBlocking(...)`: this changes the semantics (cancellable and asynchronous). The five sites are synchronous reads on background threads, so it would be a redesign with no benefit.

### D2. `OpenFileDescriptor(project, file).navigate(true)` replaces `FileEditorManager.openFile(file, true)`

This is the platform's general "open this file" entry point and already the pattern in `SpecSearchEverywhereContributor`. For a regular file it opens the file in an editor tab and focuses it, which is what the 13 sites do today. There are two edge-case differences:
- A directory is selected in the Project view instead of failing to open. No site passes a directory: every site opens a spec, change artifact, config, scratch or legacy command file, and `LegacyCleanupDialog` lists only legacy command files.
- An invalid file is a silent no-op (`canNavigate()` is false) instead of throwing. Every site already null-checks or freshly resolves the file, so this only removes a crash path.

Threading is unchanged: `navigate` must run on the EDT, like `openFile`, and every site already calls from the EDT (an action, a listener or an `invokeLater`).

*Alternatives considered:*
- `FileEditorManager.openTextEditor(descriptor, true)`: this returns null for non-text files and would need a fallback.
- `FileEditorManager.openEditor(descriptor, true)`: this works, but it's a second idiom where one already exists in the codebase.

### D3. Verified set: the floor, the last Community release, and the unified releases from 2025.3

`recommended()` is replaced by an explicit `ides { }` block:
- the floor, IntelliJ IDEA Community 2024.2 (latest patch);
- the latest IntelliJ IDEA Community 2025.2 patch;
- a `select { }` of the unified `IntelliJPlatformType.IntellijIdea`, release channel, `sinceBuild = "253"` and `untilBuild = "262.*"`, which resolves to the latest patch of 2025.3, 2026.1 and 2026.2.

The exact Gradle syntax for IPGP 2.18.1 is confirmed at apply time by running `verifyPlugin` and reading its resolved IDE list (task 1.1).

The `untilBuild = "262.*"` cap is deliberate. Without a cap, a new release line would silently add a download, and could pull a release before its verifier data is stable. The spec requires extending the set when a new stable line ships. The comment above the block says to bump the cap when 2026.3 ships.

*Alternatives considered:*
- All seven lines (today's four plus three unified): this keeps "every version" coverage, but at about three extra, larger downloads per run. That risks running out of disk on GitHub's hosted runner, where the verify job already downloads about 19.5 GB, deliberately uncached.
- Including EAP: EAP builds change every few weeks, which would make CI flaky. The Marketplace already runs EAP verification on every upload.

### D4. One static guard for both migrated calls

`ActionInvocationHygieneTest` already guards the earlier `ActionUtil.invokeAction` migration with a call-shaped regex over `src/main/java`. A sibling test, `DeprecatedPlatformApiHygieneTest`, applies the same scan (including its anti-vacuity checks) to these call shapes:
- `ReadAction\s*\.\s*compute\s*\(`
- `\.openFile\s*\(\s*\w+\s*,\s*(?:true|false)\s*\)`, the deprecated two-argument `(file, boolean)` form

The second pattern matches the argument shape rather than the receiver, so it also catches a `FileEditorManager` held in a local variable. It doesn't match the plugin's own one-argument `openFile(String)` helper in `LegacyCleanupDialog`. The guard's pattern tests lock call-versus-mention behaviour and prove that the helper isn't matched.

*Alternative considered:* extending `ActionInvocationHygieneTest`. Its name and class doc are about action invocation, so a separate class keeps each guard's purpose readable.

### D5. Exceptions are documented next to the verifier configuration

The comment above the `failureLevel` list in `build.gradle.kts` currently names `ReadAction.compute→computeBlocking` as a retained deprecation. It is rewritten to list exactly the two remaining exceptions (terminal `createShellWidget` and the Search Everywhere data API), each with the reason it can't move at the 2024.2 floor. This comment is the "documented exceptions" location the `ci` spec refers to.

## Risks / Trade-offs

- **GitHub runner disk space with three unified IDE downloads.** → The verify job is already uncached. The increase is about one IDE's worth, since two Community lines drop. If it fails on disk anyway, add a free-disk-space step to the GitHub verify job, which can be done without changing the spec.
- **Forgejo runner time.** → Verify currently takes 6.7–10 minutes uncached. One extra, larger IDE adds an estimated 3–6 minutes. The job runs on main, not on PRs.
- **Dropping 2024.3 and 2025.1 misses an API that existed only in those lines.** → The plugin compiles against 2024.2, so it can only reference APIs present there. A removal between 2024.2 and 2025.2 would show up on 2025.2. The Marketplace still verifies every line on upload.
- **`untilBuild` cap goes stale.** → The spec scenario requires extending the set when a line ships. The Marketplace verifier report for each upload lists every IDE line it checked, so a line that appears there but not in our set is the signal to bump the cap.
- **`navigate(true)` behaves differently for an unusual file type.** → For a binary or unknown file type, both APIs choose the same editor provider. The sandbox pass (task 3.4) opens one file from each site group.

## Migration Plan

This is a code-only change with no data or settings migration. To roll back, revert the commit. The plugin's compatibility range and behaviour are unchanged either way.
