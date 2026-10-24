## 1. Capture 1.15.0 fixtures (real CLI output, isolated env, sanitized to /fixture)

- [x] 1.1 Parity-corpus twins. Use the fixtures README "Durable next-generation capture" recipe: a fresh `HOME`/`XDG_*` and `OPENSPEC_TELEMETRY=0`, run `validate --all --json` default and `--strict` over `1.6.0/parity-corpus` with the npm 1.15.0 CLI, and write `fixtures/cli/1.15.0/validate-parity-corpus{,-strict}.json` with `root.path` set to `/fixture/parity-corpus`. Add `version.txt` (`1.15.0`). Verify:
  - default is 12/13 valid and strict is 8/13;
  - ignoring `durationMs`, both documents equal the committed `1.14.3/` twins;
  - `grep -rlE "/private/|/Users/"` over the new directory is empty.
- [x] 1.2 Standalone version captures. Download the `darwin-arm64` 1.15.0 release asset, check it against the release's `SHA256SUMS`, and run it under an isolated env with a PATH that has no Node. Write its `--version` stdout to `fixtures/cli/1.15.0/standalone/version.txt` and its `version` stdout to `standalone/version-cmd.txt`. Record the asset name and checksum in the fixtures README. Verify the files contain `1.15.0` and `OpenSpec 1.15.0 (standalone)`.

- [x] 1.3 AI-tool registry capture. Run `CAPTURE_VERSIONS=1.15.0 scripts/capture-ai-tools-fixtures.py` with the 1.15.0 CLI to produce `1.15.0/ai-tools.json` and `1.15.0/init-help.txt` (plus `detect/` if the script writes it). Move `AiToolRegistryContractTest.TARGET` to `1.15.0`, reading it from `gradle.properties` so it can't drift from the target again, and update the fixtures README note that names the target. Verify the test is green, and that it fails when a fake tool id is added to the plugin's registry.

## 2. Version guards

- [x] 2.1 Add `1.15.0` to `ValidatorVerdictVersionStabilityTest.FLOOR`, keeping the anchors and pinned divergence as they are. Verify the test is green and picks up the 1.15.0 twins. Then check it would catch a change: temporarily flip one strict verdict (either direction), and flip the default twin's `fenced-scenario` from invalid to valid (the default check only fails on a laxer-to-stricter difference), and confirm each fails; revert.
- [x] 2.2 Add `1.15.0` to `CliVersionAtLeastTest`'s `@ValueSource` and update its "1.3.x–1.14.x" comment. In `CliVersionTest`, add `compare("1.15.0","1.9.0") > 0`, `compare("1.15.0","1.14.3") > 0` and `!atLeast("1.14.3","1.15.0")`. Verify the `1.9.0` pair would fail under a lexical comparison (`"1.15.0".compareTo("1.9.0") < 0`).
- [x] 2.3 Bump `openspecTargetVersion` in `gradle.properties` from `1.14.3` to `1.15.0`, in the same commit as 1.1. Verify `TargetVersionSingleSourceTest` is green, and that it fails if `1.15.0/version.txt` is moved aside.

## 3. Contract test for the standalone binary

- [x] 3.1 Add a `CliDetectionServiceTest` case that feeds the captured `1.15.0/standalone/version.txt` through the `ProcessRunner` seam, using the existing `CliDetectionTestSupport` helpers. Assert the detection is available with version `1.15.0` and that `CliVersion.atLeast(version, "1.15.0")` holds. The expected value is read from the capture, not typed into the test. Verify it fails if the `--version` parse in `probeDirect` is broken, for example by temporarily returning the raw output with a suffix appended.

## 4. Documentation

- [x] 4.1 In `docs/openspec-support.md`:
  - add the `1.15.x` line: verdicts and JSON shapes identical to 1.14.3; a standalone binary, read the same way as the npm install; no new validation rule;
  - update the matrix headings to `1.3.x–1.15.x`;
  - leave the `plugin vX.Y.Z` restatements unchanged.

  Verify `DocumentationHygieneTest` is green.
- [x] 4.2 Supporting docs. Verify `CHANGELOG.md` stays user-facing and the leak grep from `CLAUDE.md` over the diff is empty.
  - README: a `1.15.x` sentence.
  - Fixtures README: a `## 1.15.0/` section covering provenance, the twins' equality with 1.14.3, and the standalone asset and checksum.
  - `CHANGELOG.md`: an `## Unreleased` Added entry, "OpenSpec CLI 1.15.x is supported, including its standalone binary".

## 5. Adopt-sync

- [x] 5.1 Upgrade the local CLI, pinned: the global CLI is an npm install under Homebrew's Node, so the user runs `! npm i -g @fission-ai/openspec@1.15.0` and confirms `openspec --version` shows `1.15.0`. Then run `openspec update`. Verify:
  - only the managed `.claude/skills/openspec-*` files regenerate (`generatedBy: "1.15.0"`);
  - `.claude/commands/opsx/` and the custom-named skills are unchanged.

  Commit the regenerated files separately (a `.claude/**`-only change has no CI build check, so fast-forward it).

## 6. Verify

- [x] 6.1 `./gradlew build` is green, including the coverage floor.
- [x] 6.2 `openspec validate support-openspec-1-15-validation --strict` is clean, using the upgraded 1.15.0 CLI.
- [x] 6.3 `caffeinate -dimsu ./gradlew uiSmoke` is green with the 1.15.0 CLI installed. The journeys that assert CLI output run against the new line.

## Workflow follow-up

- Archive the change, then close the tracker entry.
- The doc `plugin vX.Y.Z` restatements and the comparison-matrix plugin row are bumped at the v0.15.0 release cut.
