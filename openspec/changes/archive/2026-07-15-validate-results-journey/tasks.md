## 1. Journey

- [x] 1.1 Add journey 7 `validateResultsRenderCliReportedErrors` to `OpenSpecUiSmokeTest`: 1.6+ CLI guard, seed `openspec/specs/missing-shall/spec.md` (requirement without SHALL/MUST), show the tool window (registers the Console panel — run 1 proved the report falls back to a summary-only notification otherwise), invoke `OpenSpec.Validate`, assert the summary notification (`Validation failed (`) and the Console rendering the CLI-parsed `spec/missing-shall` line
- [x] 1.2 Update the `ui-smoke-journeys` delta (six → seven; new scenario) and validate the change

## 2. Verification

- [x] 2.1 Run `./gradlew uiSmoke` filtered to journey 7 and confirm green
- [x] 2.2 `./gradlew build` green; `openspec validate --all` clean
