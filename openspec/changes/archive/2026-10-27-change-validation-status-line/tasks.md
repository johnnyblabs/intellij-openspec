# Tasks

## 1. Fixtures

- [x] 1.1 Capture single-change `openspec validate <change> --type change --json` output from the real CLI in a scratch project with isolated XDG dirs: a valid change, a change with an ERROR, and a change with only WARNINGs. Sanitize root paths and commit them under `src/test/resources/fixtures/cli/` with a provenance note. Verify: each file parses as JSON and carries one `items[0]` of type `change`.

## 2. Rendering

- [x] 2.1 Add a pure `SpecPreviewRenderer.validationLine(label, ValidationResult)` that gives "passed" (plus ", M warnings" when there are warnings) or "N errors, M warnings", with an optional `openspec-validate:<change>` link. Add an insert helper that places the line after the header and the open-in-editor link. Verify with unit tests for passed, passed with warnings, failed, the escaped change name in the link, and placement.
- [x] 2.2 Add the delta operation legend to `renderChangeDeltas`, on the non-empty path only, with the D5 wording. Verify with a contract test over an existing captured `show` fixture (the legend appears exactly once) and over the empty state (no legend).

## 3. Pane wiring

- [x] 3.1 In `renderChangeDeltasPreview`, after a successful `show`, run the change-scoped validate with the Validate action's arguments, parse it with `CliOutputParser.parseJsonOutput`, insert the "Validate" line and cache the combined fragment. When validate gives no verdict, render without the line and don't cache. Verify with a contract test that feeds the captured fixtures through the same parse and render path: the valid fixture reads "Validate: passed", the ERROR fixture reads the CLI's counts, and an error envelope gives no line.
- [x] 3.2 Show the "Built-in check" line, with no link, in the CLI-unavailable placeholder, using `BuiltInValidator.validateChange`. Keep the CLI-rejected state without a line. Verify with tests on the placeholder fragment for both states.
- [x] 3.3 Expose a public change-scoped entry point on `OpenSpecValidateAction` and resolve `openspec-validate:<change>` in the pane's hyperlink listener. Verify with a unit test that the href round-trips to the change name, and that the entry point builds `validate <change> --type change --json`.

## 4. Docs and checks

- [x] 4.1 Update `docs/feature-reference.md`: the Change Deltas section gains the validation line and the legend, and the "On-model" line says a change-scoped CLI verdict is shown and nothing per spec or delta. Also update the README feature line and add a CHANGELOG Unreleased → Added entry. Verify with the leak grep and `DocumentationHygieneTest`.
- [x] 4.2 Run `./gradlew build` (exit 0) and `openspec validate change-validation-status-line --strict`. Because this touches `com.intellij.*` references only through existing APIs, confirm the pre-push verifyPlugin gate passes on push.

## Workflow follow-up

- Review in the sandbox IDE: select a valid change, a change with a scenarioless requirement, and a change with no deltas, then edit a delta and reselect.
- Archive the change, then close the trackers.
