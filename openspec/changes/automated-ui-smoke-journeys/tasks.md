# Tasks — Automated UI smoke journeys

## 1. Tooling foundation

- [x] 1.1 Resolve the current JetBrains UI-test integration artifacts for the IntelliJ Platform Gradle Plugin 2.x line (official docs, read-only) and pin versions in the version catalog
- [x] 1.2 `uiTest` source set + Gradle task: sandbox IDE with robot-server, driver-side test JVM, fixed virtual display resolution
- [x] 1.3 Extract the demo-project seeding recipe into a single shared source consumed by both the `lifecycle-testdrive` skill and the uiTest fixtures (static fixture fallback if the runner lacks CLI/network access)

## 2. Journeys

- [x] 2.1 Open-and-render journey: tool window opens, Browse tree shows seeded spec + change, workflow chips render
- [x] 2.2 Update cleanup journey: Update action → review notification present with its action
- [x] 2.3 Settings journey: Settings → Tools → OpenSpec renders with the built-in schema row
- [x] 2.4 Failure artifacts: IDE log + full-screen screenshot per failed journey; per-journey timeouts that fail rather than hang

## 3. CI wiring

- [x] 3.1 Separate workflow job: manual dispatch + release tags only; never a required PR check; Xvfb on the Linux runner; IDE archive excluded from the Gradle cache (verifier cache-scope lesson); job-level retry-once
- [x] 3.2 CI-runner verification: the workflow ships a prerequisites-check step (Xvfb + npm/CLI) and the empirical verification runs on its first manual dispatch after merge; the documented fallback is ALREADY in place regardless — the local `uiSmoke` Gradle task is wired into the release-prep checklist as a pre-tag blocker
- [x] 3.3 Release gating: the release-prep checklist gains a blocking `uiSmoke` step (the pre-tag release decision), and the CI workflow additionally runs on `v*` tags for the record

## 4. Documentation

- [x] 4.1 Testing section in the docs index describing the three layers (headless platform tests / manual test-drive skill / smoke journeys) and the execution policy; CHANGELOG only if user-visible (per changelog scope rule, likely not)
