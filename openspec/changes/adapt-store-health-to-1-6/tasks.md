## 1. Capture 1.6.0 contract fixtures

- [x] 1.1 Capture `store register` success on a fresh/config-only root from the real 1.6.0 CLI (isolated `XDG_DATA_HOME`, sanitized paths) → `src/test/resources/fixtures/cli/1.6.0/store-register-fresh-root.json`
- [x] 1.2 Capture `store doctor` for the healthy-empty store (`healthy: true`, planning dirs `present: false`) → `src/test/resources/fixtures/cli/1.6.0/store-doctor-healthy-empty.json`
- [x] 1.3 Capture the two register refusals — a root whose `openspec/config.yaml` declares `store:` (`store_root_pointer_declared`) and an invalid store pointer (`invalid_store_pointer`) → `store-register-pointer-declared.json`, `store-register-invalid-pointer.json`
- [x] 1.4 Verify each fixture parses with the CLI-version note and sanitization conventions used by the existing `1.5.0/` set (README/header comments where the set documents provenance)

## 2. Contract tests per CLI generation

- [x] 2.1 Add 1.6-generation register tests to `StoreWorksetWriteContractTest`: fresh-root success parses as `WriteResult.success == true`; both refusal fixtures parse as failure with the `status[]` message and `fix` surfaced verbatim (no raw stderr)
- [x] 2.2 Re-scope the existing `store_register_root_unhealthy` test as explicitly 1.5-generation coverage (rename/organize; assertions unchanged against the same 1.5.0 fixture)
- [x] 2.3 Add a doctor contract test for the healthy-empty payload: `StoreEntry.openspecRootHealthy` parses as `true` and diagnostics contain no entry derived from directory absence
- [x] 2.4 Add a presentation-level test pinning that a healthy-empty store renders without the unhealthy/error marker in the store listing (and that `openspecRootHealthy == false` still renders it)

## 3. Production-code audit and adjustments

- [x] 3.1 Audit `CoordinationService` doctor/register parsing against the captured 1.6 fixtures; adjust only if a fixture reveals mishandling (design decision 3 — no refusal-code special-casing)
- [x] 3.2 Grep production and test sources for the retired codes (`openspec_specs_missing`, `openspec_changes_missing`, `openspec_archive_missing`) and remove/re-scope any remaining dependency on them
- [x] 3.3 Update `StoreEntry` javadoc (currently documents 1.5-era health assumptions) to the generation-neutral semantics: health read solely from doctor's `healthy` flag; healthy-empty is a valid state
- [x] 3.4 Verify `CoordinationPanel` renders the healthy-empty store per the spec (error marker only on `openspecRootHealthy == false`); adjust the store-row rendering only if 2.4 fails

## 4. Documentation

- [x] 4.1 Update `docs/openspec-support.md` with the 1.6 store-health semantics (fresh stores healthy-empty; retired diagnostic codes; new register refusal codes)
- [x] 4.2 Update `docs/feature-reference.md` / `docs/feature-comparison-matrix.md` where they describe store registration or health behavior
- [x] 4.3 Add a user-facing `CHANGELOG.md` entry under `## Unreleased` (vendor-neutral, plugin-user wording)

## 5. Verification

- [x] 5.1 Run `./gradlew build` (full suite + coverage floor) and confirm green; ratchet the JaCoCo floor if coverage meaningfully rose
- [x] 5.2 Automate the walkthrough as uiSmoke journey 6 (`storeHealthFollowsCli16Semantics`): register-store seam property in `CoordinationPanel.onRegisterStore`, journey-scoped `XDG_DATA_HOME` isolation, CLI-1.6+ assumption guard, stops for healthy-empty rendering, pointer/confirmation refusal surfacing, and fresh-root register wiring
- [x] 5.3 Update the `ui-smoke-journeys` delta spec (six journeys; isolated-state refinement of the no-durable-state rule) and re-validate the change
- [x] 5.4 Run `./gradlew uiSmoke` filtered to the new journey and confirm green (release pipeline runs the full suite)
