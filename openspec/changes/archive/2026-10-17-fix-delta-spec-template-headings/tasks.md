## 1. Capture the client's verdict on a filled-in scaffold (contract discipline)

- [x] 1.1 Build a minimal project: a main spec `auth` containing `### Requirement: Login`, and a change `c` whose `specs/auth/spec.md` is the **new** template with one valid `### Requirement: Logout` block (plus `#### Scenario:`) under `## ADDED Requirements`. Run the real CLI at the target version (isolated `HOME`/`XDG_*`, `OPENSPEC_TELEMETRY=0`) with `validate c --type change --json`, default and `--strict`. Sanitize to `/fixture` and commit the input and the captures under `src/test/resources/fixtures/cli/<target>/scaffold-delta/`. Verify both captures are `valid:true`.
- [x] 1.2 Capture the **old** template filled with the same requirement (negative control) and verify the CLI reports `valid:false` with the "No deltas found" error. Commit it beside 1.1, so the test proves the old shape was actually broken and not just different.

## 2. Fix the template

- [x] 2.1 Change `TemplateProvider.deltaSpecTemplate` to emit the four canonical headings (`## ADDED Requirements` … `## RENAMED Requirements`), with HTML-comment guidance per section: ADDED/MODIFIED `### Requirement:` + `#### Scenario:`; REMOVED with **Reason**/**Migration**; RENAMED as ``- FROM: `### Requirement: Old` `` / ``- TO: `### Requirement: New` ``. Verify that the 1.1 fixture's delta body, minus the filled requirement, equals the template output, so the captured input is the real template.

- [x] 2.2 In `BuiltInValidator.validateDeltaSpecStructure` and `DeltaSpecInspection`, report `delta-renamed-fields` only when the RENAMED section contains at least one line matching `^\s*(?:[-*+]\s*)?(FROM|TO):` but no well-formed pair. An empty or comment-only section reports nothing. Unit tests: (a) a comment-only RENAMED section reports no `delta-renamed-fields`, and fails against the old check; (b) `FROM:` with no `TO:` is still an ERROR; (c) existing valid-pair tests stay green. Contract: run the 1.1 filled-scaffold input (empty RENAMED beside an ADDED requirement) through `BuiltInValidator` and assert no ERROR, matching the capture's `valid:true`.

## 3. Tests that fail on the old template

- [x] 3.1 Tighten `TemplateProviderTest` and `ScaffoldingContractTest`: assert each delta heading line is exactly one of the four canonical headings, and that no line matches `^## (ADDED|MODIFIED|REMOVED)\s*$`. Assert the RENAMED guidance contains `### Requirement:`. Verify these FAIL against the old template (the current `contains("## ADDED")` checks pass for both).
- [x] 3.2 Contract test: render the template, insert the 1.1 requirement under `## ADDED Requirements`, and assert the result equals the committed 1.1 input. Assert the paired captures are `valid:true` (default and strict) and the 1.2 negative control is `valid:false`. Verify the equality assertion fails against the old template.
- [x] 3.3 Assert `ScaffoldingDetectionService.isScaffoldingContent(deltaSpecTemplate("auth"))` is `true`, so an unfilled scaffold is still not counted as done.

## 4. Documentation

- [x] 4.1 Add a `## Unreleased` CHANGELOG `### Fixed` entry: "Create Delta Spec now scaffolds the section headings the OpenSpec CLI recognizes (`## ADDED Requirements` …) and the CLI's rename format; previously a delta written into the scaffold was rejected by the CLI as having no deltas." Keep it plugin-user-facing. Verify `DocumentationHygieneTest` is green.

## 5. Verify

- [x] 5.1 Run `./gradlew build` (test plus JaCoCo floor) and confirm it is green.
- [x] 5.2 Run `openspec validate fix-delta-spec-template-headings --strict` and confirm it is clean.
