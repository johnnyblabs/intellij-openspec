# Automated UI smoke journeys

## Why

The plugin's automated coverage is strong below the pixels — headless platform tests assert notification flows, tree models, parsers, and validators on every PR — but nothing exercises the *rendered* Swing UI: nobody clicks the real toolbar button, sees the real balloon, or opens the real Settings panel except a human in a `runIde` sandbox (now scripted by the `lifecycle-testdrive` skill, still human-driven). Regressions that live purely in the UI layer — a component that stops rendering, an action wired to the wrong place, a dialog that can't open — ship invisibly. JetBrains provides a UI-test stack (robot-server + remote-driver) built for exactly this.

## What Changes

- **A small set of automated UI smoke journeys** (2–3, deliberately not a port of the manual walkthrough) driving a real sandbox IDE with the plugin installed:
  1. *Open & render:* open a seeded demo project → the OpenSpec tool window opens, the Browse tree shows the seeded spec/change, the workflow chips render the DAG states.
  2. *Update cleanup journey:* trigger the Update action in a project seeded with legacy files → the review notification appears with its action.
  3. *Settings journey:* open Settings → Tools → OpenSpec → the Schemas section renders with the built-in schema row.
- **A dedicated Gradle task/source set** (`uiTest`) using the IntelliJ Platform UI-test integration (robot-server plugin + remote driver), reusing the `lifecycle-testdrive` seeding logic for the demo project.
- **Scheduled/pre-release execution, not per-PR.** UI tests boot a full IDE (multi-GB download, minutes of wall clock) and are inherently the flakiest test class; they run as a separate, manually-triggerable and release-gating CI job — never as a merge blocker on ordinary PRs.
- The manual walkthrough (`lifecycle-testdrive` skill) remains the tool for *judgment* checks (does this notification read well); the smoke journeys only assert *presence and wiring*.

## Capabilities

### New Capabilities

- `ui-smoke-journeys`: automated rendered-UI smoke coverage — scope, journeys, execution policy, and flakiness handling.

### Modified Capabilities

(none — the `ci` spec gains no per-PR requirement; the new job is defined in the new capability)

## Impact

- **Build:** new `uiTest` source set + Gradle task wiring the IntelliJ Platform testing framework's UI-test mode (robot server on the sandbox, driver in the test JVM); pinned versions.
- **CI:** a separate workflow job (manual dispatch + release tags), headless display on the Linux runner (Xvfb), reusing the existing IDE-archive caching lessons (the verifier cache exclusions apply here too).
- **Tests:** the 2–3 journeys, each with generous timeouts and screenshot-on-failure artifacts.
- **Docs:** testing section in the docs index; CHANGELOG (internal tooling — likely excluded per changelog scope rule, decided at implementation).
- **Compatibility:** dev/CI-only; no shipped-plugin surface. Runner constraints (download size, runtime, Xvfb) are first-class design inputs — see design.
