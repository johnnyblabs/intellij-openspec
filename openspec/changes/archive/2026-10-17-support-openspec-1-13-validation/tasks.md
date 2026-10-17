## 1. Capture 1.13.2 fixtures (contract discipline: real CLI output, sanitized)

- [x] 1.1 Capture the parity-corpus twins from the real 1.13.2 binary (isolated `HOME`/`XDG_*`, `OPENSPEC_TELEMETRY=0`): run `validate --all --json`, default and `--strict`, over the shared `1.6.0/parity-corpus` markdown, and write the output to `src/test/resources/fixtures/cli/1.13.2/validate-parity-corpus{,-strict}.json`. Sanitize the root to `/fixture`. Verify the totals are default 12/13 and strict 9/13, and that the normalized diff (`del(.durationMs, .items[].durationMs, .root.path)`) against the 1.12.0 twins is empty.
- [x] 1.2 Add `src/test/resources/fixtures/cli/1.13.2/version.txt` containing `1.13.2`.
- [x] 1.3 Capture the positive controls under `1.13.2/` and sanitize them:
  - `renamed-star` and `removed-star`: ``*``-bulleted delta plus a matching main spec; `validate <change> --type change --json`. Verify both are `valid:true`.
  - `removed-star`: `show <change> --json`. Verify it lists a REMOVED delta for `Works`.
  - `task-markers`: the control's `tasks.md` plus `list --json`. Verify `completedTasks:1, totalTasks:6`.
  - `invalid_item`: run `validate 'a/b' --type change --json`. Verify there are no `items`, the `status[0].severity` is `error`, and the exit code is 1.

  Commit each control's input markdown alongside its capture.

- [x] 1.4 Capture the additional 1.13.2 controls the tests compare against:
  - `renamed-star/show-change.json`, whose `rename.from`/`rename.to` are the expected sync names.
  - `renamed-star/archived-spec.md` and `removed-star/archived-spec.md`: the main spec after a real `openspec archive c --yes` on a scratch copy.
  - `renamed-star-unpaired/`: a lone `* FROM:` with no `TO:`. Verify `valid:false`.
  - `renamed-plus/`: a `+`-bulleted pair. Verify `valid:true`.
  - `task-grammar/`: a multi-change project with ordered markers, box variants, link look-alikes, fenced/commented/indented lines, CRLF, BOM and lone-CR files, captured with one `list --json`.

  Verify no machine paths leak (`grep -rl "/private/\|/Users/"` is empty).
- [x] 1.5 Add a `.gitattributes` entry `src/test/resources/fixtures/cli/1.13.2/task-grammar/** -text`, so CRLF, BOM and lone-CR bytes survive Windows checkouts (`core.autocrlf=true`). Verify `git check-attr text` on a task-grammar file reports `unset`.

## 2. RENAMED bullet parity (validator, inspection, sync)

- [x] 2.1 Add one shared RENAMED pair pattern `(?m)^\s*(?:[-*+]\s*)?FROM:\s*(.+)$\s*^\s*(?:[-*+]\s*)?TO:\s*(.+)$` in `SpecPatterns`, and point `BuiltInValidator`, `DeltaSpecInspection` and `SpecSyncService` at it, removing their three private copies. (`BuiltInValidatorRulesTest` drives the real `validateDeltaContent`, so there is no copy to update.) Verify existing `-`-bullet and bare-name RENAMED tests stay green.
- [x] 2.0 Before editing, if `fix-delta-spec-template-headings` has been archived, rebase this change's `specs/validation/spec.md` MODIFIED "Delta spec validation" block onto the current main spec. It must keep that change's "An empty RENAMED section is not an error" scenario and its narrowed "Renamed section missing FROM/TO" trigger while applying this change's any-bullet wording. Verify `openspec validate --strict` is clean and `diff` shows only the intended scenario edits against main.
- [x] 2.2 Contract test: run the captured `renamed-star` delta through `BuiltInValidator` and `DeltaSpecInspection`, and assert there is no `delta-renamed-fields`, matching the capture's `valid:true`. Verify the test FAILS when the pattern is temporarily reverted to `(?:-\s*)?`.
- [x] 2.3 In `SpecSyncService.parseRenamedSection`, normalize both names by stripping optional surrounding backticks and a leading `###\s*(?i:requirement):\s*`. Unit-test that canonical ``* FROM: `### Requirement: Works` `` / ``* TO: `### Requirement: Works well` `` renames the main-spec requirement, and that bare `FROM: Old` / `TO: New` still works. Verify the canonical-form test fails without normalization.

- [x] 2.4 Liveness anchor: run the captured `renamed-star-unpaired` delta through `BuiltInValidator` and `DeltaSpecInspection` and assert `delta-renamed-fields` IS reported (matching the capture's `valid:false`). This proves the RENAMED rule fires on `*` input, so the 2.2 "not reported" assertions can't pass because the section was never parsed. Also assert that the captured `renamed-plus` pair reports nothing. Verify the expected sync names are read from `renamed-star/show-change.json`, and that the applied main spec's requirement headers equal `renamed-star/archived-spec.md`'s.

## 3. Bullet-form REMOVED in spec sync

- [x] 3.1 In `SpecSyncService`, emit a REMOVED operation, in document order, for each ``^\s*[-*+]\s*`?###\s*Requirement:\s*(.+?)`?\s*$`` entry in a REMOVED section, alongside block-form entries. Contract test: parse the captured `removed-star` delta and assert the operation names equal the requirement names in the captured `show --json` REMOVED deltas, with any `Requirement:` prefix stripped. Then apply the operation and assert the requirement is gone from the main spec. Verify both fail against the old parser.

- [x] 3.2 Assert that the requirement headers of the main spec after applying the captured `removed-star` removal equal `removed-star/archived-spec.md`'s. Run the sync and validator arms on a `\r\n`-converted copy too, and verify the counts and verdicts are unchanged.

## 4. Task-count parity with the client's task-line grammar

- [x] 4.1 Add a `TaskLines` utility porting the client's `TASK_LINE_PATTERN` and its done rule (`"x".equalsIgnoreCase(mark)`), so that it agrees with the client on every captured input:
  - Use an explicit JS-whitespace character class (BOM, NBSP, Unicode spaces) instead of Java's `\s`.
  - Use ASCII `[0-9]` digits.
  - Escape `[` inside the lookahead class.
  - Split lines on `\n` only, use `find()`/`lookingAt()` (not `matches()`), and treat a null mark as not done.

  Contract test (`TaskLinesContractTest`): for every change in the captured `task-grammar/list.json` and `task-markers/list.json`, assert `TaskLines` on that change's committed `tasks.md` equals the captured `completedTasks`/`totalTasks`, reading the expected values from the JSON. Anti-vacuity: require at least one change whose CLI total exceeds its `- [ ]`/`- [x]` line count, and require the `t-bom`, `t-cr-only` and `t-links` changes to be present. CRLF arm: every LF control converted to `\r\n` keeps the same counts.
- [x] 4.2 Make `ApplyPromptBuilder.countTasks` delegate to `TaskLines`, keeping its signature. Contract test: count the captured `task-markers` `tasks.md` and assert it equals the captured `list --json` `completedTasks`/`totalTasks` (1/6). Verify it fails against the old prefix-match implementation (which gives 1/1).
- [x] 4.3 Make `VerificationService` derive its complete, in-progress (`~`) and incomplete (any other non-`x` mark) buckets from `TaskLines`. Verify the existing `[~]` and `[ ]` Verify tests stay green, and add a test that `*`- and `1.`-bulleted not-done tasks block archive.

## 5. Item-less CLI error response is a failed run

- [x] 5.1 In `CliOutputParser.parseJsonOutput`, throw a dedicated unchecked exception when the root has no `items` array and a `status` entry has `severity` equal to `error` (case-insensitive). Contract test: parsing the captured `invalid_item` fixture throws, and an `items`-bearing capture (for example the 1.13.2 parity twin) parses exactly as before. Verify the first test fails against the current parser, which returns a PASS.
- [x] 5.2 Extract `OpenSpecValidateAction`'s CLI routing, combine and fallback into a package-private static method that takes the CLI call as a lambda (a pure move; production passes `() -> CliRunner.run(project, cliArgs(target))`). Platform tests feed the captured `invalid_item` response:
  - (a) A CHANGE target whose built-in result fails yields `!passed` and source `built-in`. The old code yields a CLI PASS.
  - (b) A SPEC target with only a WARNING follows the strict flip.
  - (c) The captured `renamed-star/validate-change.json` still yields source `cli`, passed.

## 6. Version guards and target version

- [x] 6.1 Add `1.13.2` to `ValidatorVerdictVersionStabilityTest.FLOOR`, and `1.13.0` to `CliVersionAtLeastTest`'s `@ValueSource`. Verify the stability guard auto-discovers the 1.13.2 twins and passes.
- [x] 6.2 Extend `CliVersionTest` with the anti-lexical pair `compare("1.13.0","1.9.0") > 0`, plus `1.13.0 > 1.12.0` and `!atLeast("1.12.0","1.13.0")`. Verify the `1.9.0` assertion fails under lexical comparison.
- [x] 6.3 Bump `openspecTargetVersion` from `1.12.0` to `1.13.2` in `gradle.properties`. Verify `TargetVersionSingleSourceTest` passes (target, committed corpus and `version.txt` agree).

## 7. Documentation (vendor-neutral, public mirror)

- [x] 7.1 In `docs/openspec-support.md`, add the `1.13.x` line (laxer RENAMED/REMOVED bullets mirrored; new 1.13.1 ERRORs not adopted, fallback laxer by policy; task-count parity; error-response handling) and bump the matrix heading to `1.3.x–1.13.x`. Leave the `plugin vX.Y.Z` restatements alone. Verify `DocumentationHygieneTest` is green.
- [x] 7.2 Add a README `1.13.x` mention and a fixtures README `## 1.13.2/` section documenting that the twins are byte-identical to 1.12 apart from `durationMs`, plus each positive control's provenance and capture recipe.
- [x] 7.3 In `docs/feature-reference.md`, update task-progress counting to say it follows the client's task-line grammar (any bullet, numbered, `[]`, `[ x ]`) and that RENAMED/REMOVED accept any bullet in validation and sync. Verify no stale "`- [ ]` only" wording remains (`grep -n "\- \[ \]" docs/feature-reference.md`).
- [x] 7.4 Add a `## Unreleased` CHANGELOG entry covering: OpenSpec CLI 1.13.x supported; `*`/`+`-bulleted RENAMED no longer flagged; bullet REMOVED and canonical-form RENAMED now applied by spec sync; task progress now matches the CLI's counts; a rejected item id no longer validates as passed. Keep it plugin-user-facing only. Verify `DocumentationHygieneTest` is green.

## 8. Adopt-sync

- [x] 8.1 Update the local CLI to 1.13.2 and run `openspec update`. Verify the managed `.claude/skills/openspec-*` and `.claude/commands/opsx/*` regenerate to `generatedBy: "1.13.2"`, and that the custom-named tracker and release skills are untouched (`git status` shows no change under them). Commit the regenerated managed surfaces.

## 9. Verify

- [x] 9.1 Run `./gradlew build` (test plus JaCoCo floor) and confirm it is green. Ratchet the floor only on a visible rise.
- [x] 9.2 Run `./gradlew verifyPlugin`, since the change touches the inspection and validator classes that the pre-push hook gates on `com.intellij.*` changes. Confirm no new problems.
- [x] 9.3 Run `openspec validate support-openspec-1-13-validation --strict` and confirm it is clean.
