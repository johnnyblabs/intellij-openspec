## Why

Two CI hygiene gaps surfaced during v0.2.10 release prep and are still observable today:

1. **No concurrency cancellation on the build/verify pipelines.** When a PR or branch receives a second push, the original CI run continues to completion (5–14 minutes) alongside the new run. v0.2.10's retrigger commit demonstrated this directly — two `verifyPlugin` jobs ran in parallel until the first one finished naturally. Wasted minutes, wasted runner capacity, two sets of cache contention. Pure overhead.
2. **The Forgejo workflow still pins `actions/cache@v3` and `actions/upload-artifact@v3`.** Both are deprecated; the runner emits deprecation warnings on every build. The GitHub workflow is already on `@v4` (since v0.2.10's CI sweep). The bumps are backwards-compatible for our usage.

A third item in `project_ci_caching_optimization` memory — "Enable Gradle build cache via `gradle.properties`" — was already shipped in commit `eef8fa2` (2026-03-18). The memory was stale on that point; this change confirms it is in fact a no-op item and corrects the memory at archive time.

## What Changes

### Concurrency cancellation (3 files)

Add a top-level `concurrency:` block to each workflow file. Two distinct policies depending on the workflow's trigger:

- **`.github/workflows/build.yml`** (build + verify on `push` to main and `pull_request`):
  ```yaml
  concurrency:
    group: ${{ github.workflow }}-${{ github.ref }}
    cancel-in-progress: true
  ```
  A new push to the same ref cancels the in-flight run.

- **`.forgejo/workflows/build.yaml`** (same triggers, Forgejo-side):
  Same block as above. Forgejo Actions implements GitHub-compatible concurrency syntax.

- **`.github/workflows/release.yml`** (tag-pushed, single-release-per-tag):
  ```yaml
  concurrency:
    group: ${{ github.workflow }}
    cancel-in-progress: false
  ```
  Different policy: `group` is workflow-scoped (not ref-scoped), and `cancel-in-progress: false` so releases serialize rather than cancel. A second tag pushed during an in-flight Marketplace publish should queue, never cancel — concurrent `publishPlugin` runs could race against the JetBrains Marketplace API.

### Action version bumps (1 file, 4 occurrences)

In `.forgejo/workflows/build.yaml`, replace `@v3` with `@v4`:
- `uses: actions/cache@v3` → `@v4` (twice — build job + verify job)
- `uses: actions/upload-artifact@v3` → `@v4` (twice — test results + plugin artifact)

GitHub workflows already use `@v4`; this is a Forgejo-only catch-up. Clears the deprecation warnings the runner emits on every build.

### Memory correction

Update `project_ci_caching_optimization` memory at archive time to note the Gradle build cache work is already done (referencing commit `eef8fa2`) so future-me doesn't try to re-do it.

## Capabilities

### New Capabilities
<!-- None — modifying an existing `ci` capability. -->

### Modified Capabilities
- `ci`: adds a new requirement "Concurrency cancellation for in-flight runs" covering the build/verify and release policies. The four other existing `ci` requirements (Dual-job pipeline, Build job, Plugin Verifier compatibility, Dependency caching, Runner environment, Release pipeline) are untouched.

The action version bumps are an internal implementation detail of the existing "Dependency caching" requirement — no spec change needed for those (the spec describes WHAT, the workflow file is the HOW).

## Impact

- **CI workflow files:**
  - `.github/workflows/build.yml` — 4 lines added (concurrency block at top level)
  - `.github/workflows/release.yml` — 4 lines added (concurrency block, different policy)
  - `.forgejo/workflows/build.yaml` — 4 lines added (concurrency) + 4 single-line bumps
- **Specs:** `openspec/specs/ci/spec.md` gains one ADDED requirement (~10 lines).
- **No code changes** — pure CI-config edits.
- **No test impact** — Gradle test suite is unaffected; the change is visible only at CI runtime.
- **Plugin behavior:** unchanged for users.
- **Risk:** very low. Each of the three workflow edits is independent; a regression in one is easy to isolate via a revert of that file alone.

## References

- Tracker: the linked issue (the original tracker — has the same three-point fix sketch)
- Tracker: the linked issue
- Source memory: `project_ci_caching_optimization` (stale on point 3 — fixed at archive time)
- Prior gradle.properties caching commit: `eef8fa2` (2026-03-18 "Add Gradle dependency caching to CI workflow")

No new Forgejo/Plane trackers created for this change — the existing tracker has the canonical body. When this change archives, the archive flow closes the linked tracker issue.
