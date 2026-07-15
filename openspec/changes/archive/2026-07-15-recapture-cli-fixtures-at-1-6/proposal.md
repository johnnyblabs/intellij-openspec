## Why

The plugin's CLI contract fixtures at `src/test/resources/fixtures/cli/` (root, versionless) were captured from pre-1.6 CLI generations, while npm `latest` now installs OpenSpec CLI 1.6.0 — whose `validate`, `status`, and `update` output has materially changed (reworded/re-pathed validate issues, a new INFO issue level with a `line` field, additive `status --json` keys, new update-migration preamble). Until the fixture set is re-captured at 1.6.0, the contract tests prove nothing about the CLI generation new users actually run.

Tracker: this change is linked to an existing tracker issue via the gitignored `.tracking.yaml` sidecar (per the repository's tracker-sidecar convention).

## What Changes

- Capture a 1.6.0-generation fixture set under `src/test/resources/fixtures/cli/1.6.0/`, mirroring the versionless root filenames: `status` (×2 DAG states), `instructions` (×3 artifacts), `validate` (seeded to exercise every issue class incl. the new INFO+`line` issue), `schema-validate` (×3), `schema-which` (×3), `templates`, and `update-*.txt` (×3, via a 1.3.1-initialized legacy project updated by the 1.6.0 CLI). All captures from the real CLI under isolated `HOME`/`XDG_*`, machine paths sanitized to the `/fixture/...` convention.
- Freeze the versionless root fixture set as legacy-generation coverage: no moves, no re-pointing, no deletions. Add a provenance manifest (`fixtures/cli/README.md`) recording each file's capturing CLI version (or best-evidence era), capture recipe, and whether it is recapturable.
- Document the `coordination-*.json` fixtures as permanently pinned: the `workspace`/`context-store`/`initiative` commands were removed upstream at 1.5.0, so those 1.4-generation captures can never be refreshed and remain the only parse coverage for the still-supported 1.4.x line.
- Add explicitly named 1.6-generation contract tests alongside the untouched legacy tests (the per-generation pattern established by the store-health change): nested V16 test classes in `CliContractTest`, `SchemaToolingContractTest`, and `UpdateOutputParserContractTest`, plus one `UpdateLegacyCleanupFlowTest` case feeding the 1.6 pending-migration output end-to-end.
- No production behavior change is expected — the parsers were probed as tolerant of the 1.6 output — but if a capture exposes a real parser gap, the fix lands with its failing contract test in this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ci`: the contract-test requirement grows fixture-lifecycle rules — contract fixtures SHALL be organized per CLI generation under version-named directories, fixture provenance SHALL be recorded in a committed manifest, legacy captures for still-supported generations SHALL be retained (not re-pointed or deleted) while that generation remains supported, and fixtures whose source command no longer exists upstream SHALL be documented as pinned rather than silently staling.

## Impact

- **Tests**: `CliContractTest`, `SchemaToolingContractTest`, `UpdateOutputParserContractTest`, `UpdateLegacyCleanupFlowTest` gain 1.6-generation coverage; `CoordinationContractTest` gains javadoc documenting the pinned 1.4 fixtures. Existing legacy-generation assertions are preserved verbatim.
- **Test resources**: new files under `src/test/resources/fixtures/cli/1.6.0/`; new `src/test/resources/fixtures/cli/README.md` provenance manifest; versionless root files unchanged.
- **Production code**: the capture exposed one real parser gap, fixed in this change — `CliOutputParser.parseJsonOutput` scanned the validate JSON with a bracket-delimited regex, which 1.6's bracketed issue paths (`requirements[0]`) truncate, silently dropping errors; it now parses the JSON structurally (behavior otherwise unchanged, verified by the untouched legacy contract tests).
- **Docs/build**: one user-facing `CHANGELOG.md` Fixed entry for the dropped-errors fix. JaCoCo floor unchanged (no meaningful rise). No new IntelliJ Platform APIs; no Plugin Verifier or uiSmoke impact.
