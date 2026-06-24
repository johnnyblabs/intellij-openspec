## 1. Validator code changes

- [x] 1.1 `BuiltInValidator.java:248-252` — replace the `config-missing` ERROR + short-circuit with `return new ValidationResult(true, List.of(), "built-in")`. Mirrors upstream's `readProjectConfig() → null // No config is OK`.
- [x] 1.2 `BuiltInValidator.java:270-273` — remove the `if (config.getVersion() == null || config.getVersion().isEmpty())` branch entirely (the `config-version-required` WARNING). Keep the `else if (!VersionSupport.allVersions().contains(...))` branch but restructure: now an unconditional `if (config.getVersion() != null && !config.getVersion().isEmpty() && !VersionSupport.allVersions().contains(...))` — fires `config-version-unknown` only when a value is set and not recognized.
- [x] 1.3 `BuiltInValidator.java:295-298` — remove the `config-profile-recommended` WARNING entirely.
- [x] 1.4 `VersionSupport.java:13-16` — change `V1_2.requiredConfigFields` from `Set.of("schema", "version")` to `Set.of("schema")`. Drops `config-field-required` ERROR for missing `version:`. The loop at `BuiltInValidator.java:282-293` stays — it now only checks `schema`.

## 2. Spec updates

- [x] 2.1 `openspec/specs/validation/spec.md` — locate the "Config validation" requirement and update text + scenarios per the change's delta spec under `specs/validation/spec.md`. Drop scenarios for `config-missing` (ERROR when file absent), `config-version-required` (WARNING when version absent), `config-field-required` (ERROR per VersionSupport), and `config-profile-recommended` (WARNING when profile empty). Add scenarios for the absent-config-passes path.
- [x] 2.2 Confirm `openspec validate align-config-contract-with-cli --strict` → green before commit.

## 3. Tests

- [x] 3.1 `BuiltInValidatorRulesTest.java` (or a new `BuiltInValidatorConfigTest`) — extract the validator's config logic into a test-callable shape if not already. The existing structure uses a hand-mirrored `validateSpec`/`validateDeltaStructure` helper for the spec-level rules; the config rules don't currently have an equivalent. Decide: either invoke `BuiltInValidator.validateConfig` directly via a small integration fixture, or mirror its logic in a test helper for unit coverage. Prefer the integration path now that the contract is smaller.
- [x] 3.2 Add `configMissing_isClean()` — fixture with no `openspec/config.yaml`, assert `validateConfig` returns `passed=true` and zero issues.
- [x] 3.3 Add `configWithoutVersion_isClean()` — fixture with `schema: spec-driven` only (no `version:`), assert no `config-version-required`, no `config-field-required`, no `config-profile-recommended` issues.
- [x] 3.4 Add `configWithoutProfile_isClean()` — fixture with `schema:` + `version:` but no `profile:`, assert no `config-profile-recommended`.
- [x] 3.5 Add `configWithoutSchema_isError()` — fixture with `version: "1.2.0"` but no `schema:`, assert ERROR `config-schema-required` fires (the one rule that stays).
- [x] 3.6 Add `configWithUnknownVersion_warns()` — fixture with `version: "9.9.9"`, assert WARNING `config-version-unknown` still fires (the else-if path, unchanged by this change).

## 4. Self-validation

- [x] 4.1 Run `openspec validate --all` against THIS project — expect green. (This project's own config.yaml has all fields populated, so behavior shouldn't change for the project itself.)
- [x] 4.2 Run `openspec validate --all` after temporarily moving this project's `openspec/config.yaml` aside — expect green (was ERROR before this change).
- [x] 4.3 Run `openspec validate --all` after temporarily deleting only the `version:` line — expect green (was ERROR + WARNING before this change).

## 5. Verify

- [x] 5.1 `./gradlew test --tests "*BuiltInValidator*"` — expect green including new tests.
- [x] 5.2 `./gradlew test` — full suite green.
- [x] 5.3 `./gradlew buildPlugin` — clean compile.
- [x] 5.4 Sandbox (optional, `./gradlew runIde`): open a fresh project that has no `openspec/config.yaml`, confirm IDE no longer surfaces `config-missing` ERROR in the Problems tool window or batch validation output.

## 6. Land

- [x] 6.1 Run `/mirror-change-trackers align-config-contract-with-cli` to create the tracker entries and write the gitignored `.tracking.yaml` sidecar.
- [x] 6.2 Commit and push.
- [ ] 6.3 After implementation lands, archive via `/openspec-archive-change align-config-contract-with-cli` and sync the delta into `openspec/specs/validation/spec.md`.
- [ ] 6.4 `close-change-trackers` closes the linked tracker entries when archive lands.

## 7. Follow-up

- [ ] 7.1 Future: `OpenSpecSettings.getEffectiveVersion` refactor — derive effective version from `SchemaService` CLI-runtime detection instead of consulting `OpenSpecConfig.getVersion()` as a fallback. Removes the last functional dependency on the plugin-internal `version:` field. Defer until the `V1_X` axis decision lands.
- [ ] 7.2 Consider whether to drop the `requiredConfigFields` indirection in `VersionSupport` entirely once it shrinks to `{schema}`. Argument for keeping: future config-format eras may genuinely require new fields. Argument against: the loop in the validator becomes a single inline check. Decide when the V1_X axis decision lands.
- [ ] 7.3 Documentation pass: README + `docs/feature-reference.md` likely still describe config.yaml as required. Update to reflect the optional-with-defaults model after this change ships.
