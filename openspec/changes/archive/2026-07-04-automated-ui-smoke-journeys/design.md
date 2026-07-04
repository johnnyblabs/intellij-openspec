# Design — Automated UI smoke journeys

## Context

Three test layers exist or are planned: headless platform tests (every PR, stable, already assert flows like the Update cleanup notification), the `lifecycle-testdrive` skill (scripted sandbox setup, human judgment), and — this change — automated rendered-UI smoke tests. The stack of record is JetBrains' own UI-test tooling: a robot-server plugin loaded into the sandbox IDE exposes the Swing component tree; a driver in the test JVM locates components and interacts. This repo's CI history is a first-class design input: the verifier job previously died on multi-GB IDE archive caching (resolved by scoping cache exclusions), and the self-hosted CI runner is Linux/headless — UI tests need Xvfb and the same cache discipline.

## Goals / Non-Goals

**Goals:**
- Catch pure-UI wiring/rendering regressions (tool window fails to open, action missing from toolbar, dialog fails construction) that headless tests structurally cannot see.
- Keep the suite small (2–3 journeys), diagnosable (screenshot on failure), and out of the per-PR critical path.

**Non-Goals:**
- Not a port of the manual walkthrough — judgment checks (wording, layout, feel) stay human via `lifecycle-testdrive`.
- No per-PR gating, ever, until the suite has demonstrated a quarter of stability — flaky merge blockers are worse than no UI tests.
- No pixel/screenshot comparison assertions (theme/font/platform variance makes them noise).
- No coverage requirements on UI-test code (it exercises, it isn't exercised).

## Decisions

1. **JetBrains stack over generic tooling.** Robot-server + remote driver understand Swing and the IntelliJ component model; generic OS-level automation (cliclick, AppleScript, xdotool) is strictly worse on every axis. Pin the tooling versions in the version catalog.
2. **Journeys assert presence and wiring, not content prose.** Locators target component types and accessible names; assertions are "tool window exists, tree has N roots, notification with action appeared" — resilient to copy edits.
3. **Seeding reuses the `lifecycle-testdrive` recipe** (old-CLI init for legacy files, lowercase-header spec, mid-lifecycle change) extracted into a shared script/fixture builder so skill and uiTest can't drift apart. Where CLI-network access is unavailable in CI, the seeded tree is committed as a static fixture directory instead (decided at implementation by what the runner allows).
4. **Execution policy: manual dispatch + release tags.** The CI job runs on demand and as part of the release pipeline (a `v*` tag must have green smoke journeys before publish); ordinary PRs never wait on it. Failure artifacts: IDE log + full-screen screenshot per failed journey.
5. **Runner budget:** the job downloads one IDE archive (same version as `runIde` target), excluded from the Gradle cache exactly like the verifier's (the cache-scope lesson), under Xvfb with a fixed resolution. Wall-clock budget ≤ 15 min; a journey exceeding its timeout fails with artifacts rather than hanging the job.

## Risks / Trade-offs

- [Flakiness erodes trust] → small suite, generous per-step timeouts, presence-level assertions, retry-once policy at the job level (not per-assertion), and the not-a-merge-blocker rule.
- [Tooling churn across platform versions] → pinned versions; the journeys compile against the same target IDE as `runIde`, so a platform bump updates both together.
- [Runner can't take the load] → the job is optional-by-default; if the runner proves too weak, the fallback documented outcome is "run locally before release" via a Gradle task, still automated, just not CI-hosted.

## Migration Plan

Additive dev tooling; no shipped-plugin change. Lands as a separate CI job; release-prep gains a check that the smoke job is green for the tagged commit.

## Open Questions

- Exact artifact names/versions of the current JetBrains UI-test integration for the platform Gradle plugin 2.x line — resolved at implementation from the official docs current at that time (upstream consulted read-only).
- Whether the self-hosted CI runner supports the required job configuration for Xvfb — verified empirically at implementation (the runner is known to reject newer action versions, so job composition must stay within its supported set).
