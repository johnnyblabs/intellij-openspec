## 1. Provenance manifest and freeze

- [x] 1.1 Add `src/test/resources/fixtures/cli/README.md`: per-file provenance table (capturing CLI version or best-evidence era, capture recipe pointer, recapturable y/n), the root-freeze rule (new captures go to `<version>/` dirs only), and the pin note for `coordination-*.json` (source commands removed upstream at 1.5.0)
- [x] 1.2 Add the matching pin note to `CoordinationContractTest` javadoc (1.4-generation fixtures, permanently unrecapturable, retained as 1.4.x coverage)

## 2. Capture the 1.6.0 fixture set

- [x] 2.1 Set up the isolated capture env (fresh temp `HOME`/`XDG_DATA_HOME`/`XDG_CONFIG_HOME`/`XDG_STATE_HOME`) and a seed project via `openspec init`; script the `/fixture/...` path sanitization
- [x] 2.2 Capture `1.6.0/status.json` and `1.6.0/status-with-context.json` at two DAG states of a seeded change (proposal-only → mixed statuses with `missingDeps`; artifacts added → done-heavy), sanitizing `planningHome`/`changeRoot`/`artifactPaths` paths
- [x] 2.3 Capture `1.6.0/instructions-{proposal,specs,tasks}.json` at staged DAG states (no files → proposal; proposal done → specs; design done, specs absent → tasks), sanitizing `changeDir`
- [x] 2.4 Capture `1.6.0/validate.json` from a seed exercising every issue class: valid spec, valid spec with WARNING, missing-SHALL spec (`requirements[0]` ERROR), delta-less change (ERROR), valid change with non-canonical level-3 delta header (INFO + `line`); sanitize the top-level `root.path`
- [x] 2.5 Capture `1.6.0/schema-validate-{clean,missing-template,broken}.json` (schema init → validate; delete a template → validate; corrupt `schema.yaml` → validate) and `1.6.0/schema-which-{builtin,project,shadowing}.json` + `1.6.0/templates-builtin.json` (keep the `@fission-ai/openspec/schemas/spec-driven` tail when sanitizing the node-modules prefix)
- [x] 2.6 Capture `1.6.0/update-{legacy-pending,legacy-pending-regenerated,clean}.txt`: project initialized by `npx -y @fission-ai/openspec@1.3.1 init --tools junie .` under the isolated env, updated by the 1.6.0 CLI (pending → `--force` → pending-again for regenerated; fresh 1.6 init for clean); record any regenerated-loop behavior change as a finding
- [x] 2.7 Grep-verify no machine-specific paths remain anywhere under `1.6.0/` (`grep -rn "$(whoami)\|/private/tmp\|/Users/" ...`) and fill in the manifest rows for every new file

## 3. 1.6-generation contract tests (legacy assertions untouched)

- [x] 3.1 `CliContractTest`: add nested `StatusContractV16`, `InstructionContractV16`, `ValidateContractV16` classes with exact-value assertions against the 1.6 captures — including per-index artifact statuses/`missingDeps`/`applyRequires`/`actionContext` (proves Gson tolerates the additive 1.6 keys), the `requirements[0]` missing-SHALL ERROR with the real 1.6 message, the delta-less-change ERROR, and `warningCount()==0` despite the seeded WARNING-on-valid and INFO-on-valid issues with `passed()==false`
- [x] 3.2 `SchemaToolingContractTest`: add nested V16 classes mirroring the three existing nests with 1.6-exact values (issue path, `Parse error` prefix, `shadowedSources`, template-map ordering)
- [x] 3.3 `UpdateOutputParserContractTest`: add nested `RealOutputContractV16` — exact ordered file list from the 1.6 pending capture (proves the `Migrated: ...` preamble doesn't break header recognition), regenerated list, clean-yields-empty
- [x] 3.4 `UpdateLegacyCleanupFlowTest`: add one case feeding the 1.6 pending fixture through `handleUpdateResult` and asserting the cleanup notice is raised
- [x] 3.5 If any capture exposed a real parser gap, fix the parser in this change alongside its failing contract test (residual risk: `CliOutputParser` key-order-sensitive regexes)

## 4. Verification

- [x] 4.1 Run `./gradlew build` (full suite + coverage floor) and confirm green
- [x] 4.2 Check the JaCoCo report; ratchet the floor in `build.gradle.kts` only if coverage meaningfully rose
- [x] 4.3 Re-validate the change (`openspec validate --all`) and confirm the `ci` delta parses clean
