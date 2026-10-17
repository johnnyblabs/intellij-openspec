## 1. Contract

- [x] 1.1 Capture `openspec validate c1 --json` from CLI 1.14.3 and 1.3.0 (isolated `HOME`/`XDG_*`; 1.3.0 via `npx -y @fission-ai/openspec@1.3.0`) for four deltas: bare-only, bare beside ADDED, unpaired-only, unpaired beside ADDED. Store them in `fixtures/cli/<version>/validate-renamed/<case>/` with the main spec and the delta, and document provenance in the fixtures README. Verify: the captured verdicts match the design's table and paths are sanitized.

## 2. Shared reader

- [x] 2.1 `SpecMerge.summarize(deltaContent)`: sections present, entry count, unpaired renames (D1). Verify: unit tests for each of the four shapes plus REMOVED bullets and an empty RENAMED beside ADDED. Mutation check: counting bare lines as entries fails a test.

## 3. Rules

- [x] 3.1 `BuiltInValidator`: `delta-no-entries` with upstream wording; `delta-renamed-fields` from the summary's unpaired renames with upstream wording and nothing for bare lines; `delta-none-found` counting entries (D2). Verify: a contract test whose built-in verdict equals the 1.14.3 capture for each case, and is valid wherever 1.3.0 is, except unpaired beside ADDED (D3, asserted explicitly); existing `delta-renamed-fields` tests updated deliberately. Mutation checks: dropping the no-entries rule, reporting bare lines, and counting files again each fail a test.
- [x] 3.2 `DeltaSpecInspection` uses the same summary for RENAMED. Verify: inspection tests that bare lines beside an entry produce no problem, and that an unpaired header-form `FROM:` does.

## 4. Docs and verify

- [x] 4.1 CHANGELOG `### Fixed` (without the CLI, a delta whose only content is renames written with bare names is now reported invalid, as OpenSpec reports it; bare lines beside other changes are ignored, as OpenSpec ignores them). Verify: entry present; a leak grep over the diff is clean.
- [x] 4.2 Run `./gradlew build`, `./gradlew verifyPlugin` and `openspec validate align-builtin-validator-renames --strict`. Verify: all pass.
