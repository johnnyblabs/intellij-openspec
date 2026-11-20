## 1. Configuration error response (CLI present)

- [x] 1.1 In `CliOutputParser`, add `CliConfigErrorException extends CliErrorResponseException`, carrying `code`, `message` and `fix`. `parseJsonOutput` throws it for an item-less `Failed` envelope whose code is `unparseable_config` or `unreadable_config`, and throws the parent type for every other no-verdict case, as now. Verify:
  - `Validate116ContractTest` asserts the subclass and its three fields are read from each captured 1.16.0 fixture;
  - the captured `invalid_item` and `no_openspec_root` responses still throw the parent type and not the subclass;
  - the test fails when the code check is removed, or when the subclass is thrown for every envelope.
- [x] 1.2 Add `OpenSpecValidateAction.configErrorResult(Project, CliConfigErrorException)`. It builds a failed `ValidationResult` with exactly one ERROR:
  - path: the resolved `config.yaml`;
  - line: 1;
  - text: `message + " " + fix`;
  - rule: `cli`, as on every CLI-reported issue;
  - source: `cli`.

  `cliOrFallback` catches the subclass before the generic catch and returns this result for every target and in strict mode, with no built-in merge. In `OpenSpecValidateCliErrorFallbackTest`, drive `cliOrFallback` with each captured fixture for a whole-project, a change and a strict target, and assert:
  - the run failed;
  - there is one issue, an ERROR;
  - its text contains the captured `message` and `fix`;
  - its path ends with `openspec/config.yaml`;
  - its source is `cli`.

  Also assert that the captured `invalid_item` and `no_openspec_root` responses still return the built-in verdict. Verify the test fails when the subclass catch is removed.
- [x] 1.3 In `SpecPreviewRenderer.cliValidationLine`, catch the subclass and render the line from the same result (`CliConfigErrorException.toResult`), so the pane shows one error and no warnings. Other no-verdict runs still return null. Verify with a pane test fed each captured fixture, and confirm it fails when the catch is removed.
- [x] 1.4 Correct the `combineWithCli` and `cliOrFallback` javadocs, and the comment "the CLI's validate never reads config.yaml", to say the CLI checks only that the file can be read and parsed. Verify by grepping `src/main` for "never reads config": no hits.

## 2. Built-in config check (CLI absent)

- [x] 2.1 `ConfigService.reload` records a nullable `LoadProblem(kind, detail)`, exposed through the `current()` snapshot:
  - `UNREADABLE` for an `IOException`;
  - `UNPARSEABLE` for a SnakeYAML `YAMLException`, with the detail being the first line of its message;
  - nothing for a `ClassCastException` or any other mismatch.

  `getConfig()` still returns defaults, and a successful reload clears the problem. In `ConfigServiceTest`, cover:
  - a malformed file: `UNPARSEABLE`, and the detail is non-empty;
  - a top-level scalar and a top-level list: no problem, and defaults are returned;
  - a valid file after a malformed one: the problem is cleared.

  Verify the test fails when the problem is recorded for `ClassCastException` too.
- [x] 2.2 When the loaded snapshot carries a problem, `BuiltInValidator.validateConfig` returns `passed=true` with exactly one WARNING at `config.yaml`, `config-unparseable` or `config-unreadable`, carrying the detail, and skips the field checks. Keep both rules out of `CLI_MIRRORING_STRICT_WARNINGS`. In `BuiltInValidatorTest`, assert:
  - the malformed-file case, with no `config-schema-required` INFO alongside;
  - that `applyStrictFallbackVerdict(result, true)` still passes.

  Verify the test fails if the rule is added to the flip set, or if the result fails.
- [x] 2.3 Unreadable file. Make a mode-000 `config.yaml` (skip on Windows, and when the test runs as root) and assert `UNREADABLE` and the `config-unreadable` WARNING. If VFS reads it anyway on some platform, cover the `IOException` branch through the reload seam instead, and record why.

## 3. Documentation

- [x] 3.1 Docs:
  - `docs/openspec-support.md`, the 1.16 "Broken `config.yaml`" bullet: with the CLI, Validate now fails and shows the CLI's fix; without it, a non-failing warning is shown.
  - `docs/feature-reference.md`, the Validate section: one sentence on a broken `config.yaml`.
  - `CHANGELOG.md`, `## Unreleased` → `### Fixed`: "Validate reports a failure, with the CLI's fix, when `openspec/config.yaml` can't be read or parsed, instead of passing."

  Verify `DocumentationHygieneTest` passes, and the CLAUDE.md leak grep over the diff is empty.

## 4. Verify

- [x] 4.1 `./gradlew build` passes, including the coverage floor.
- [x] 4.2 `openspec validate validate-config-error-fails --strict` passes with CLI 1.16.0.
- [x] 4.3 Manual check in `runIde` with CLI 1.16.0 on the seeded demo. Break `openspec/config.yaml`, run Validate, and confirm:
  - the notification says the project failed with 1 error;
  - the console lists the CLI's message and fix under a `config.yaml` link that opens the file;
  - the Change Deltas pane line for a selected change shows 1 error.

  Restore the file afterwards.
