## Context

Three CI workflow files (`build.yml`, `release.yml`, `forgejo/build.yaml`) handle compile, test, Plugin Verifier, signing, Marketplace publish, and GitHub Release creation. None of them today declare a `concurrency:` block, so runs accumulate when pushes come in faster than runs complete. Concurrency cancellation is a stock GitHub Actions feature (also supported by Forgejo Actions) requiring nothing but a top-level YAML block — no plugin code touches CI semantics.

A parallel detail: the Forgejo workflow lags the GitHub workflow on action versions (`@v3` for cache and upload-artifact, vs `@v4` on the GitHub side). This was overlooked during the v0.2.10 CI sweep that bumped the GitHub workflow.

## Goals / Non-Goals

**Goals:**
- A second push to the same branch / PR cancels any in-flight build or verify run for that ref. Saves 5–14 minutes per superseded run.
- Releases serialize (do not cancel). A second `v*` tag push during an in-flight Marketplace publish waits; never cancels.
- The Forgejo workflow stops emitting `actions/cache@v3` and `actions/upload-artifact@v3` deprecation warnings.
- The `ci` spec gains the concurrency contract as an explicit requirement so a future contributor can't silently drop the block.

**Non-Goals:**
- Configuration cache enablement (`org.gradle.configuration-cache=true`). Separate optimization; requires auditing the Kotlin DSL build script for compat.
- `setup-gradle@v3` migration. Larger refactor of the cache-action-vs-dedicated-setup-action question; deferred per `project_ci_caching_optimization` memory.
- Runner-host changes (job-duration limits, memory). That's the territory of the runner-fix tracker (Forgejo runner verifyPlugin fix).
- Changing the actual cache key derivation, restore-keys, or which paths are cached. Out of scope — this change is hygiene, not redesign.

## Decisions

**Use `${{ github.workflow }}-${{ github.ref }}` for the build group, not just `${{ github.ref }}`.** Including the workflow name in the group prevents an unrelated workflow from accidentally cancelling a build run if they happen to share a ref scope. Alternative considered: just `${{ github.ref }}` (per the original tracker sketch). Rejected because workflow-scoped grouping is the more defensible default — it costs nothing and protects against future additions of unrelated workflows that share branch state.

**Use `${{ github.workflow }}` (not ref-scoped) for the release group, with `cancel-in-progress: false`.** Releases must serialize, not cancel — concurrent `publishPlugin` runs against the JetBrains Marketplace API could race (the Marketplace API surface is not idempotent for upload, and we want the linear log "v0.3.0 published, then v0.3.1 published" rather than two interleaved publishes). The ref-scoped grouping is wrong here because every tag has a different ref; we'd never get any serialization benefit. Workflow-scoped grouping creates a single global lock per workflow file. Alternative considered: literal `group: release`. Rejected only for symmetry with the build workflow's variable-based grouping; behavior is equivalent.

**Don't add a spec requirement covering "action versions stay current."** Two reasons. (1) The existing "Dependency caching" requirement describes WHAT the pipeline caches and HOW the cache key is derived — the action's major version is an implementation detail under that. (2) The plugin-core spec has an analog "API compatibility" requirement covering plugin code's avoidance of deprecated APIs; a parallel CI requirement would be the right shape for that hygiene, but doesn't belong scoped to this small change. If the team wants that contract codified, it deserves its own change.

**Bump the Forgejo workflow only.** GitHub's workflow is already on `@v4` (verified by reading the file). No-op on the GitHub side.

**Memory correction at archive time.** The stale memory finding (Gradle build cache was already enabled) is itself worth recording. Update `project_ci_caching_optimization` body to mark item 3 as "shipped in eef8fa2 (2026-03-18)" so future-me reads the memory and immediately knows it's already done. Better than silently letting the memory rot.

## Risks / Trade-offs

**A legitimate in-flight build gets cancelled because the new push is unrelated noise** (e.g., a docs-only commit while a test was running). → Trade-off accepted: the build is cheap to re-run, and the user (or PR author) is in control of when to push. Net win on bookkeeping clarity is large. If this becomes a real friction, we can scope the concurrency block to `if: github.event_name == 'pull_request'` only.

**Forgejo Actions implementation diverges from GitHub on concurrency semantics.** → Mitigation: Forgejo's Actions implementation is broadly Gitea-compatible and explicitly aims at GitHub-syntax parity for `concurrency:` blocks. If the runner doesn't honor `cancel-in-progress: true` correctly, the worst outcome is "concurrency block silently ignored" — same behavior as today. No regression possible.

**Action version bump introduces an undocumented breaking change.** → Mitigation: `actions/cache@v4` and `actions/upload-artifact@v4` are mature majors (released 2024 H1) with extensive deployment history. The breaking changes between v3 and v4 affect APIs we don't use (e.g., the path expansion rules for `upload-artifact` changed but we use single-path uploads). Cross-verified by reading the GitHub workflow's existing `@v4` usage — it works identically to our Forgejo `@v3` usage.

**Release-pipeline concurrency lock prevents emergency back-to-back releases.** Hypothetical: a v0.3.0 publish fails partway through, user pushes v0.3.0-hotfix tag while v0.3.0's run is still spinning on the failure. → With `cancel-in-progress: false`, the hotfix queues behind the failed run. Trade-off accepted: this is the safer default. If the user needs to force-publish, they can cancel the failed run manually via the UI before pushing the hotfix tag.

## Migration Plan

None. Concurrency is a runtime-only behavior; existing in-flight runs at the moment of the merge complete normally. Subsequent runs inherit the new behavior. Rollback is a 3-line revert per file.

The memory correction is a single-line edit to a documentation file in `~/.claude/projects/.../memory/`. No code impact.
