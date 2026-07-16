## Why

No uiSmoke journey exercises the Validate action's results surface — the notification that lists every parsed issue. Journey 4 covers editor highlighting, a different path. The gap is not hypothetical: the recently fixed validate-parsing bug (CLI 1.6's bracketed issue paths truncating the old regex scan) made CLI-reported errors silently vanish from exactly this notification, and nothing rendered-UI-level would have caught it. A seventh journey pins the full path: Validate action → CLI run → JSON parse → merged result → rendered notification line.

Tracker: this change is linked to an existing tracker issue via the gitignored `.tracking.yaml` sidecar (per the repository's tracker-sidecar convention).

## What Changes

- Add uiSmoke journey 7 (`validateResultsRenderCliReportedErrors`): seed a missing-SHALL spec into the demo project, open the tool window (the Console panel registers with its contents), trigger `OpenSpec.Validate` programmatically, and assert both result surfaces — the summary notification reports the failure, and the OpenSpec Console renders the **CLI-parsed** error line, distinguished from the built-in validator's duplicate by its `spec/<id>` path form, so the assertion cannot be satisfied by the built-in path alone. Requires a 1.6+ host CLI (skipped below, matching journey 6's guard), since the seeded error exercises the 1.6 bracketed-path shape that motivated the journey.
- `ui-smoke-journeys` spec: the suite grows six → seven with the new scenario.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ui-smoke-journeys`: the journey-suite requirement grows a seventh journey — the Validate-results journey asserting CLI-reported errors reach the rendered notification.

## Impact

- **Tests**: `OpenSpecUiSmokeTest` gains journey 7 (no production code changes; no new platform APIs).
- **Docs**: none (test infrastructure; no CHANGELOG entry).
