# Tasks — Automated UI smoke journeys

## 1. Tooling foundation

- [ ] 1.1 Resolve the current JetBrains UI-test integration artifacts for the IntelliJ Platform Gradle Plugin 2.x line (official docs, read-only) and pin versions in the version catalog
- [ ] 1.2 `uiTest` source set + Gradle task: sandbox IDE with robot-server, driver-side test JVM, fixed virtual display resolution
- [x] 1.3 Extract the demo-project seeding recipe into a single shared source consumed by both the `lifecycle-testdrive` skill and the uiTest fixtures (static fixture fallback if the runner lacks CLI/network access)

## 2. Journeys

- [ ] 2.1 Open-and-render journey: tool window opens, Browse tree shows seeded spec + change, workflow chips render
- [ ] 2.2 Update cleanup journey: Update action → review notification present with its action
- [ ] 2.3 Settings journey: Settings → Tools → OpenSpec renders with the built-in schema row
- [ ] 2.4 Failure artifacts: IDE log + full-screen screenshot per failed journey; per-journey timeouts that fail rather than hang

## 3. CI wiring

- [ ] 3.1 Separate workflow job: manual dispatch + release tags only; never a required PR check; Xvfb on the Linux runner; IDE archive excluded from the Gradle cache (verifier cache-scope lesson); job-level retry-once
- [ ] 3.2 Verify the CI runner supports the needed job configuration empirically (it is known to reject newer action versions); if it cannot, implement the documented fallback: a local `uiSmoke` Gradle task wired into release-prep instead of CI
- [ ] 3.3 Release pipeline: `v*` tag requires green smoke journeys before publish; release-prep checklist gains the check

## 4. Documentation

- [ ] 4.1 Testing section in the docs index describing the three layers (headless platform tests / manual test-drive skill / smoke journeys) and the execution policy; CHANGELOG only if user-visible (per changelog scope rule, likely not)
