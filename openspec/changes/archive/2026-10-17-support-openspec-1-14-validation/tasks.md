## 1. Capture 1.14.3 fixtures (real CLI output, isolated env, sanitized to /fixture)

- [x] 1.1 Parity-corpus twins: `validate --all --json`, default and `--strict`, over `1.6.0/parity-corpus` → `fixtures/cli/1.14.3/validate-parity-corpus{,-strict}.json` (`root.path` → `/fixture/parity-corpus`), plus `version.txt` (`1.14.3`). Verify: default 12/13, strict 8/13. The only verdict difference from the 1.13.2 twins is `nameless-change` going invalid under `--strict`, carrying the "not carried into the main spec by archive" warning.
- [x] 1.2 A positive control per new warning, each project tree plus `validate … --json` (default and `--strict`):
  - `unknown-meta`: an unrecognized `.openspec.yaml` key;
  - `xkey-only`: only an `x-*` key, valid in both modes;
  - `too-long`: a requirement over 500 characters;
  - `archive-dropped-text`: text before the first requirement in a delta section.

  Verify each is valid by default and invalid under `--strict`, except `xkey-only`.
- [x] 1.3 Additive-shape captures from one small project that carries the new keys: `show <change> --json` (with `name`), `status --change <c> --json` (with `warnings`), `instructions <artifact> --json` and `instructions apply --json` (with `tasks[].sourcePath`/`line`). Verify no machine paths leak (`grep -rl "/private/\|/Users/"` is empty).

## 2. Cross-generation guard

- [x] 2.1 Redesign `ValidatorVerdictVersionStabilityTest`'s strict arm: every captured generation's strict valid-set ⊆ the `1.6.0` strict anchor's valid-set. Keep the shared item-id-set and minimum-generations checks. Pin the divergence: generations ≥ `1.14.2` must equal the anchor's valid-set minus exactly `{nameless-change}`; earlier generations must equal it exactly. Update the class javadoc. Verify with mutation, where each must fail: (a) drop `nameless-change` from the pinned divergence; (b) a copied twin with a second strict failure; (c) temporarily move the 1.14.3 strict twin aside.
- [x] 2.2 Add `1.14.3` to `FLOOR`, add `1.14.0`/`1.14.3` to `CliVersionAtLeastTest`, and add `CliVersionTest` anti-lexical checks (`1.14.0 > 1.9.0`, `1.14.0 > 1.13.0`, `!atLeast("1.13.2","1.14.0")`). Verify the `1.9.0` pair fails under lexical comparison.
- [x] 2.3 Bump `openspecTargetVersion` from `1.13.2` to `1.14.3`. Verify `TargetVersionSingleSourceTest` passes.

## 3. Contract tests for the new behaviour

- [x] 3.1 A contract nest per warning control in `CliContractTest`, reading every expected value from the capture: the warning's level, path and message stem, and the default-valid/strict-invalid verdict through `CliOutputParser.parseJsonOutput` (strict surfaces the WARNING on a `valid:false` item). `xkey-only` must be valid in both modes. Verify a test fails if the parser stops mapping WARNING or reads the verdict from severities.
- [x] 3.2 Tolerance contracts: run the 1.14.3 `show`, `status`, `instructions` and `instructions apply` captures through the plugin's real parsers (`ChangeDeltaModel`/change-deltas parsing, `parseChangeStatus`, the instructions parser). Assert the fields they read today equal what the captures contain (delta operations and names, artifact statuses and `isComplete`, instruction text). If a parser fails on a new key, fix it, with a test that fails before the fix.
- [x] 3.3 Built-in fallback stays laxer: run each warning control's input through `BuiltInValidator` and assert no ERROR (and, in default mode, a pass). This matches the "fallback MAY remain laxer" decision.

## 4. Documentation

- [x] 4.1 In `docs/openspec-support.md`, add the `1.14.x` line (additive shapes tolerated; new warnings not adopted; the strict guard is now subset with the 1.14.2 divergence) and update the matrix headings to `1.3.x–1.14.x`. Leave the `plugin vX.Y.Z` restatements alone. Verify `DocumentationHygieneTest` is green.
- [x] 4.2 README `1.14.x` sentence; a fixtures README `## 1.14.3/` section (provenance, the twins' single strict difference, each control); a CHANGELOG `## Unreleased` Added entry (OpenSpec CLI 1.14.x supported). Keep the CHANGELOG user-facing.
- [x] 4.3 Correct the project `CLAUDE.md` passage on `.openspec.yaml`. It says unknown keys are *silently stripped*; since 1.14.0 the CLI warns about them, with `x-*` exempt from 1.14.2, and the known keys now include `skip_specs` and `retire_capabilities`. Restate that tracker IDs stay in the gitignored sidecar because `.openspec.yaml` is published. Verify the edit adds no private infrastructure identifiers (run the standard pre-commit leak grep from `CLAUDE.md` on the diff).

## 5. Adopt-sync

- [x] 5.1 Upgrade the local CLI, pinned (the user runs `! npm i -g @fission-ai/openspec@1.14.3`), then run `openspec update`. Verify only the managed `.claude/skills/openspec-*` and `.claude/commands/opsx/*` regenerate (`generatedBy: "1.14.3"`) and the custom-named skills are untouched. Commit the regenerated files separately.

## 6. Verify

- [x] 6.1 Run `./gradlew build` and confirm it is green.
- [x] 6.2 Run `./gradlew verifyPlugin` (the range may be extended by the sibling deprecation change) and confirm it is Compatible with no new deprecated or removal usages.
- [x] 6.3 Run `openspec validate support-openspec-1-14-validation --strict` and confirm it is clean.
