## 1. Console panel helpers (encapsulate mapping + resolution)

- [x] 1.1 Add a severity → `ConsoleViewContentType` mapping helper on `OpenSpecConsolePanel` (ERROR→`ERROR_OUTPUT`, WARNING→`LOG_WARNING_OUTPUT`, INFO→`LOG_INFO_OUTPUT`/`NORMAL_OUTPUT`); no hardcoded colors.
- [x] 1.2 Add `printWarning(String)` / `printInfo(String)` alongside the existing `printOutput`/`printError`.
- [x] 1.3 Add `printFileHyperlink(Project, String filePath, int oneBasedLine, String linkText, ConsoleViewContentType fallbackType)`: resolve via `LocalFileSystem.findFileByPath` (relative → resolve against project base first), build `OpenFileHyperlinkInfo(project, vFile, line-1)` (guard `line<=0`→0), and on null resolve print `linkText` as plain `fallbackType` text with no link. Never construct `OpenFileHyperlinkInfo` with a null file.
- [x] 1.4 Factor the resolution + line-mapping into a pure, package-visible helper (returns the resolved `VirtualFile` or null, and the 0-based line) so the wiring is unit-testable without a live editor.

## 2. Result formatter (the render path)

- [x] 2.1 Replace the single-`StringBuilder` block in `OpenSpecValidateAction.showValidationResults`/`showInConsole` with a structured render: verdict+target line, count line (errors/warnings/infos), then group-by-file.
- [x] 2.2 Implement deterministic grouping/ordering: files error-containing → warning-only → info-only, ties by path asc; within a file by line asc, ties by severity (ERROR<WARNING<INFO). CLI pseudo-path (unresolvable, line 0) issues grouped last under their identifier as plain headers.
- [x] 2.3 Per issue: print the `L<line>` (or file-header path) token via `printFileHyperlink`, and the `SEVERITY  message [rule]` segments via the severity-colored `print`. Keep the target label from `describeTarget(target)` in the header so scoped runs read as scoped.
- [x] 2.4 Clean-pass / empty state: `✓ Validation PASSED — <target>` + `No issues found.`; passing-with-warnings still lists the issues grouped by file.
- [x] 2.5 Confirm the notification balloon path is unchanged (summary only, no issue enumeration) and does not double-fire alongside the console.

## 3. Tests

- [x] 3.1 Unit-test the pure resolution/wiring helper: resolvable absolute path → `OpenFileHyperlinkInfo` at `line-1`; `line<=0` → line 0; unresolvable/`type/id` pseudo-path → null (plain-text branch). Use a real temp file for the resolvable case.
- [x] 3.2 Unit-test the formatter's structure on structural markers (not exact markup): header contains verdict/target/counts; group order is errors-first then by path; within-file order by line; CLI pseudo-path issues grouped last; clean-pass and pass-with-warnings states. Both ERROR-present and all-clean inputs.
- [x] 3.3 Unit-test the severity→content-type mapping covers all three severities and introduces no fourth.
- [x] 3.4 Contract check: build a mixed `ValidationResult` from the existing captured CLI validate fixtures (`fixtures/cli/1.6.0/validate-*.json`) plus a synthesized built-in issue with an absolute path, and assert the formatter produces links for the absolute-path issue and plain text for the CLI `type/id` issue — proving the degradation branch against real CLI shapes, not a hand-authored path.

## 4. Demo surfaces (full-MVP envelope)

- [x] 4.1 Add one screenshot-tour frame of the colored, grouped, hyperlinked validation console after a Validate run on a seeded project containing a deliberate spec ERROR plus a WARNING (shows all severities + links). Reuse the existing tour plumbing; wrap any fragile step in `runCatching`.
- [x] 4.2 Add one release-gated uiSmoke journey: seed a known-error project, invoke Validate, assert the console tab renders and its text contains the expected verdict/count line and at least one issue row. Assert content, not click-navigation.

## 5. Verify & finish

- [x] 5.1 `./gradlew build` green (tests + JaCoCo floor); ratchet the coverage floor upward with margin only if new coverage clears it comfortably (never to the measured max).
- [x] 5.2 `./gradlew verifyPlugin` green (new `com.intellij.execution.filters.*` refs) — the pre-push gate will run it; confirm no since-build surprise.
- [x] 5.3 intellij-code-reviewer + test-engineer AUDIT pass on the diff (EDT discipline, hyperlink/VFS handling; no vacuous tests).
- [x] 5.4 Update CHANGELOG `## Unreleased` (user-facing: "Validation results now render grouped by file with clickable file:line links and per-severity coloring"). Vendor-neutral, no tracker IDs.
