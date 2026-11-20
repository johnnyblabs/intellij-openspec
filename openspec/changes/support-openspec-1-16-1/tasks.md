## 1. Capture 1.16.1 fixtures (real CLI output, isolated env, sanitized to /fixture)

- [ ] 1.1 Parity-corpus twins and version. Use the fixtures README recipe with the npm 1.16.1 CLI and write `fixtures/cli/1.16.1/` twins. Verify the default and strict verdicts equal the 1.16.0 twins, ignoring `durationMs`.
- [ ] 1.2 AI-tool registry, `init --help` and workflow ids. Run the existing capture scripts with `1.16.1` and verify each equals the 1.16.0 capture apart from the version.
- [ ] 1.3 Instructions. Repeat the design and specs instruction captures with 1.16.1.
- [ ] 1.4 Closing-run headings. Capture a spec with `## Purpose ##`, `## Requirements ##` and `### Requirement: ... ###` next to its plain twin, and verify both validate the same way.
- [ ] 1.5 Unrecognized delta section. Capture a change with a requirement under `## DEPRECATED Requirements` next to a clean twin, in default mode, and verify the error and the pass.

## 2. Contract tests

- [ ] 2.1 Add parse-level tests for the captures in 1.5: the unrecognized-section change reads as failing with the error, and the clean twin reads as passing.
- [ ] 2.2 Add parse-level tests for the captures in 1.4: the closing-run spec reads the same as its plain twin.

## 3. Target and guards

- [ ] 3.1 Bump `openspecTargetVersion` in `gradle.properties` from `1.16.0` to `1.16.1`.
- [ ] 3.2 Extend the floor and at-least assertions in the version-guard tests to include `1.16.1`.

## 4. Adopt-sync

- [ ] 4.1 With the CLI at 1.16.1, run `openspec update`. Review the diff. Restore any customization with `git checkout HEAD -- .claude/skills/ .claude/commands/`.

## 5. Verify

- [ ] 5.1 `./gradlew build` passes, including the coverage floor.
- [ ] 5.2 `openspec validate support-openspec-1-16-1 --strict` passes.
