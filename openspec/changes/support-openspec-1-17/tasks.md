## 1. Capture 1.17.0 fixtures (real CLI output, isolated env, sanitized to /fixture)

- [x] 1.1 Parity-corpus twins and version. Use the fixtures README recipe with the installed 1.17.0 CLI and write `fixtures/cli/1.17.0/` twins. Verify the default and strict verdicts equal the 1.16.1 twins, ignoring `durationMs`.
- [x] 1.2 AI-tool registry, detect fixtures, `init --help` and workflow ids. Run the existing capture scripts with `1.17.0` and verify each equals the 1.16.1 capture apart from the version.
- [x] 1.3 Instructions. Repeat the design and specs instruction captures with 1.17.0 and compare them with 1.16.1.
- [x] 1.4 Target-scoped captures. Run `scripts/capture-explore-fixtures.py`, `scripts/capture-workflow-invocations.py`, `scripts/capture-explore-context-fixtures.py` and `scripts/capture-update-dry-run-fixtures.py` against the 1.17.0 CLI, through its installed package file rather than `npx`. Verify each equals its 1.16.1 capture apart from the version, with no cache paths.
- [x] 1.5 Record the 1.17.0 set and its provenance in the fixtures README.

## 2. Target and guards

- [x] 2.1 Bump `openspecTargetVersion` in `gradle.properties` from `1.16.1` to `1.17.0`.
- [x] 2.2 Add `1.17.0` to the floor set in the verdict version-stability test and to the at-least assertions in the version-comparison test, and add an ordering pair showing `1.17.0` sorts above `1.9.0`.

## 3. Adopt-sync

- [x] 3.1 With the CLI at 1.17.0, run `openspec update`. Review the diff. Restore any customization with `git checkout HEAD -- .claude/skills/ .claude/commands/`, and keep the new version stamp.

## 4. Documentation

- [x] 4.1 Update the supported range to `1.3.x`–`1.17.x` in `docs/openspec-support.md` and `docs/feature-comparison-matrix.md`, and add the 1.17 assessment to the CLI version notes.
- [x] 4.2 Add a user-facing `## Unreleased` entry to `CHANGELOG.md`.

## 5. Verify

- [x] 5.1 `./gradlew build` passes, including the coverage floor.
- [x] 5.2 `openspec validate --all --strict` passes.
