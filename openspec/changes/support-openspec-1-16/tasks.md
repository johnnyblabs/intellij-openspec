## 1. Capture 1.16.0 fixtures (real CLI output, isolated env, sanitized to /fixture)

- [x] 1.1 Parity-corpus twins and version. Use the fixtures README "Durable next-generation capture" recipe with the npm 1.16.0 CLI and write `fixtures/cli/1.16.0/validate-parity-corpus{,-strict}.json` and `version.txt`. Verify:
  - default is 12/13 valid and strict is 8/13;
  - ignoring `durationMs`, both documents equal the `1.15.0/` twins;
  - `grep -rlE "/private/|/Users/"` over the new directory is empty.
- [x] 1.2 AI-tool registry. Run `CAPTURE_VERSIONS=1.16.0 scripts/capture-ai-tools-fixtures.py` to produce `1.16.0/ai-tools.json` and `init-help.txt`. Verify both equal 1.15.0's apart from the version, and drop `detect/` if it equals `1.14.3/detect/`.
- [x] 1.3 Workflow ids. Run `scripts/capture-workflow-ids.py` against the npm 1.16.0 package. Verify `core` and `all` equal 1.15.0's.
- [x] 1.4 Instructions. Repeat the `1.15.0/instructions/` recipe with 1.16.0. Verify `instructions-design.json` matches 1.15.0's, and that `instructions-specs.json` differs only by the scenario-step bullet.
- [x] 1.5 Standalone version captures. Download the `darwin-arm64` 1.16.0 release asset and check it against the release's `SHA256SUMS`. Run it under an isolated env with a PATH that has no Node, and write `standalone/version.txt` (`--version`) and `standalone/version-cmd.txt` (`version`). Record the asset name and checksum in the fixtures README.
- [x] 1.6 Scenario-step warning. In the `1.15.0/validate-change/` project shape, capture `validate step-warning-change --type change --json` with and without `--strict`, as `1.16.0/validate-change/step-warning-change{,-strict}.{json,exit}`. The change adds one requirement whose scenario has a WHEN step and an AND step, but no THEN. Commit the input project beside the outputs as `1.16.0/validate-change/step-warning-project/`. Verify:
  - default: `valid: true`, one WARNING, exit 0;
  - strict: `valid: false`, the same WARNING, exit 1.
- [x] 1.7 Configuration error responses. Add `validate-unparseable-config` and `validate-unreadable-config` cases to `scripts/capture-error-fixtures.py`: invalid YAML, and a mode-000 `config.yaml` that is restored afterwards. Write them under `1.16.0/errors/`. Verify each `.stdout` is an item-less `status` envelope with the matching `code`, and each `.exit` is `1`. Diff the no-root store-suggestion case against `1.14.3/errors/`, and commit it only if it differs.

- [x] 1.8 Target-scoped Explore and invocation fixtures. Run `scripts/capture-explore-fixtures.py` (writes `explore/` and `update/`) and `scripts/capture-workflow-invocations.py` with 1.16.0. `ExploreInstructionsTest`, `ExplorePathsContractTest` and `WorkflowInvocationContractTest` read the copies for the target version. Verify every file equals 1.15.0's apart from the generated skills' `generatedBy` stamp.

## 2. Version guards and target

- [x] 2.1 Bump `openspecTargetVersion` in `gradle.properties` to `1.16.0`, in the same commit as section 1. Verify `TargetVersionSingleSourceTest` and `AiToolRegistryContractTest` are green. Then verify `TargetVersionSingleSourceTest` fails when `1.16.0/version.txt` or the strict twin is moved aside, and that `AiToolRegistryContractTest` fails when a fake tool id is added to the plugin's registry.
- [x] 2.2 Add `1.16.0` to `ValidatorVerdictVersionStabilityTest.FLOOR`, keeping the anchors and the pinned divergence. Verify it is green, then check it would catch a change:
  - flipping one strict verdict in the 1.16 strict twin fails;
  - flipping the default twin's `fenced-scenario` from invalid to valid fails.

  Revert both.
- [x] 2.3 Add `1.16.0` to `CliVersionAtLeastTest`'s `@ValueSource`. In `CliVersionTest`, add `compare("1.16.0","1.9.0") > 0`, `compare("1.16.0","1.15.0") > 0` and `!atLeast("1.15.0","1.16.0")`.
- [x] 2.4 Make `WorkflowParityContractTest` and `CoreWorkflowsContractTest` read the fixture for the target version from `gradle.properties`, and assert the fixture's `cliVersion` equals the target. Verify each fails when `1.16.0/workflows.json` is moved aside, and when a fake id is added to the plugin's gated set or a core id is dropped.

## 3. Contract tests for the new captures

- [x] 3.1 Standalone. Add a `CliDetectionServiceTest` case, or parametrize the 1.15 case, that feeds `1.16.0/standalone/version.txt` through the `ProcessRunner` seam. Assert the detected version is the one read from the capture. Verify it fails when the `--version` parse in `probeDirect` is broken.
- [x] 3.2 Instructions. Add `1.16.0/instructions/instructions-specs.json` to `RegenerationNotePromptTest`'s fixtures. Assert its `instruction` differs from 1.15.0's, and that the lines 1.16 adds (computed from the two parsed fixtures) appear in the built prompt. Verify it fails when the prompt drops or truncates the instruction.
- [x] 3.3 Scenario-step warning. Through the plugin's validate-output parser, assert:
  - the default capture passes;
  - the strict capture fails and carries a WARNING whose message is read from the capture.

  Verify it fails when the verdict is derived from "no ERROR" instead of `valid`, and when WARNING issues are skipped.
- [x] 3.4 Configuration errors. Add a versioned overload to `CliFixtures.error`. For each capture, assert:
  - the reader reports a failed envelope with that `code`;
  - the captured exit code is 1;
  - `CliOutputParser.parseJsonOutput` throws `CliErrorResponseException`.

  Do not assert the Validate action's fallback routing (separate work). Verify it fails when an absent `items` is treated as present, and when the first `status` entry is picked regardless of severity.

## 4. Documentation

- [x] 4.1 In `docs/openspec-support.md`, add the `1.16.x` line (scenario-step warning, configuration error response, everything else unchanged) and update the matrix headings to `1.3.x–1.16.x`. Leave the `plugin vX.Y.Z` restatements unchanged. Verify `DocumentationHygieneTest` is green.
- [x] 4.2 Supporting docs: a `1.16.x` sentence in the README, a `## 1.16.0/` section in the fixtures README (provenance, equality with 1.15.0, the standalone asset and checksum, the step-warning project, the error cases), and an `## Unreleased` Added entry in `CHANGELOG.md`, "OpenSpec CLI 1.16.x is supported". Verify `CHANGELOG.md` stays user-facing and the leak grep from `CLAUDE.md` over the diff is empty.

## 5. Adopt-sync

- [x] 5.1 With the local CLI at 1.16.0, run `openspec update`. Verify only the managed `.claude/skills/openspec-*` and `.claude/commands/opsx/` files regenerate, and that the custom-named skills are unchanged. Commit the regenerated files separately.

## 6. Verify

- [x] 6.1 `./gradlew build` is green, including the coverage floor.
- [x] 6.2 `openspec validate support-openspec-1-16 --strict` is clean with the 1.16.0 CLI.
- [x] 6.3 `caffeinate -dimsu ./gradlew uiSmoke` is green with the 1.16.0 CLI. Check that no seed scenario picks up the new step warning and that every version-gated skip reason still holds.
- [x] 6.4 Re-run the screenshot tour under `caffeinate`. The Validate-notification wording and shot 09's failing seed count CLI issues, so a new warning there would change the count.
