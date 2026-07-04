# Design — Expand the automated UI walkthroughs

## Context

The Starter/Driver infrastructure (pinned 242.26775.15, `uiSmoke` task, shared seeded fixture, `out/perf-startup` artifacts) landed with the first three journeys, all verified green locally. This change adds the three walkthrough stops that automation doesn't cover yet. Lessons already banked from round one apply: calibrate against the actual 242 jars (docs describe a newer line), assert rendered text with `hasText` (tree/label paint is not a component), route EDT-required JMX calls through `withContext(OnDispatcher.EDT)`, and let failure screenshots drive selector fixes.

## Goals / Non-Goals

**Goals:**
- Automate the remaining checklist stops: editor validator parity, feedback dialog guard, archive guard.
- Keep every journey presence-level, non-mutating (dialogs exited via cancel; no archive move, no feedback sent).
- Keep the fixture seeded from the single shared script.

**Non-Goals:**
- No quick-fix application journey (mutating an editor document under the driver is a flake magnet; the quick-fix is covered by the headless platform test).
- No Direct-API-dependent journeys (Fast-Forward/Continue/Verify semantic paths need API keys; out of scope for smoke).
- No policy change: still manual dispatch + release gating, never per-PR.

## Decisions

1. **Editor-parity assertions go through the highlighting daemon, not pixel/markup scraping.** The 242 driver SDK ships a `DaemonCodeAnalyzer` facade (`DaemonCodeAnalyzerKt`); the journey opens the file, waits for highlighting, and asserts on the highlight list — presence of the targeted keyword-in-header diagnostic on the bad fixture, absence of requirement-recognition complaints on the lowercase-header fixture. Exact facade signatures are read from the 242 jars at implementation (round-one discipline).
2. **The bad fixture is seeded, not inlined.** `scripts/seed-lifecycle-demo.sh` gains `openspec/specs/keyword-in-header/spec.md` (keyword only in the header); the committed testData fixture is regenerated from the script. The manual walkthrough template gets a line pointing at it, keeping stop 2 of the manual checklist and this journey on the same file.
3. **Dialog journeys assert-and-cancel.** Feedback: action → dialog present (`byTitle`) → OK blocked on empty message (assert the OK button disabled state or the validation surface) → Escape. Archive: action on the 1/4-complete change → confirmation surface present → Escape/Cancel → assert the change directory still exists in the fixture copy (cheap filesystem check from the test JVM, since the project is a temp copy the test owns).
4. **Suite stays one class per concern:** the three new journeys join `OpenSpecUiSmokeTest` (same fixture lifecycle helpers) unless the file crowds past readability, in which case a sibling `OpenSpecUiWalkthroughTest` splits dialog journeys out — decided at implementation by size, not upfront.

## Risks / Trade-offs

- [Dialog focus/typing flakiness] → dialogs are asserted by presence, driven by action invocation (not mouse), exited by Escape; no typing except the settings journey already shipped.
- [Highlighting-daemon wait races] → the daemon facade's own wait API plus the suite's `waitUntil` poller; the targeted diagnostic is asserted by its unique message fragment ("move the keyword onto the requirement body line").
- [Suite runtime grows to ~6 IDE boots] → still minutes, still dispatch/release-only; the policy absorbs it. If wall clock matters later, journeys can share a boot per fixture state — deferred until it hurts.

## Migration Plan

Additive to the uiSmoke suite; regenerated fixture is committed in the same change.

## Open Questions

- Exact 242 signatures of the `DaemonCodeAnalyzer` driver facade — resolved from the jars at implementation, per round-one practice.
