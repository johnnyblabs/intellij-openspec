## 1. SchemaService — new known-set helper

- [x] 1.1 Added `public Set<String> getKnownSchemaNames()` returning the union of `VersionSupport.V1_2.getValidSchemas()` and `listSchemas()` (when `isSchemaSupported()`), wrapped in `Collections.unmodifiableSet`.
- [x] 1.2 No new cache state needed — the union is computed on-demand from the already-cached `listSchemas()` result; existing `clearCache()` invalidation path covers it.
- [x] 1.3 Javadoc explains the union semantics, CLI-availability fallback, and that this is the canonical recognition check for validators.

## 2. BuiltInValidator — use the union, not VersionSupport directly

- [x] 2.1 Replaced `version.getValidSchemas().contains(changeSchema)` at the change-schema site with `getKnownSchemaNames().contains(...)`. Added private `getKnownSchemaNames()` helper with defensive null-fallback to built-ins.
- [x] 2.2 Updated the WARNING text on `change-schema-incompatible` to reference the known-set and call `describeSchemaSourceStatus()` for CLI-status detail + an actionable suggestion (restart project or refresh schemas).
- [x] 2.3 Same replacement at the `config-schema-invalid` site — shares the helper, identical text shape adjusted for config context.
- [x] 2.4 Grep confirmed no remaining direct `version.getValidSchemas()` calls in `BuiltInValidator` except the defensive fallback in `getKnownSchemaNames()` itself.

## 3. VersionSupport — Javadoc-only update

- [x] 3.1 Updated Javadoc on `getValidSchemas()` to clarify the role shift to "built-in fallback set" + cross-reference `SchemaService.getKnownSchemaNames()` as the canonical check.
- [x] 3.2 No field value or signature changes — documentation-only.

## 4. Tests

- [x] 4.1 New `SchemaServiceTest.KnownSchemaNames` nested class with 6 tests: `cliUnavailable_returnsOnlyBuiltIns`, `cliBelowFloor_returnsOnlyBuiltIns`, `cliAvailableWithBuiltInsOnly_returnsBuiltIns`, `cliAvailableWithFork_returnsBuiltInsPlusFork`, `cliListEmpty_stillReturnsBuiltIns` (defensive case), `returnedSetIsImmutable`.
- [x] 4.2 `ConfigVersionValidationTest`: added `customForkedSchema_inKnownSet_passes` plus dedicated overload of the test helper that takes the known-set directly (existing tests unchanged).
- [x] 4.3 Regression: added `typoSchema_notInKnownSet_warns` and `cliUnavailable_customSchemaNotKnown_warns` plus a sanity `builtInSchema_inKnownSet_passes`.
- [x] 4.4 `BuiltInValidatorTest` not present in the codebase; coverage handled at the unit level via `ConfigVersionValidationTest`. Documented absence.

## 5. Spec sync at archive time

- [ ] 5.1 During `/opsx:archive`, sync the `validation` delta into `openspec/specs/validation/spec.md`. The delta:
  - MODIFIES the "Config validation" requirement (text + 5 new/replaced scenarios for the schema-name semantics).
  - MODIFIES the "Change validation" requirement (text + 1 new scenario for custom-forked schema acceptance).

## 6. Verification

- [x] 6.1 `./gradlew test` — BUILD SUCCESSFUL; all tests pass (10 new test methods, no regressions).
- [x] 6.2 `openspec validate schema-validation-cli-runtime-driven --strict` — change validates cleanly.
- [ ] 6.3 Manual spot-check deferred to release-prep: in IDE sandbox, run `openspec schema fork spec-driven my-test-flow`, create a change declaring `schema: my-test-flow`, open `.openspec.yaml` in the editor, confirm no `change-schema-incompatible` warning. Then introduce a typo (`my-test-flowww`) and confirm the warning fires.

## 7. Archive-time tracker closure

- [ ] 7.1 Close Forgejo #215 with archival comment.
- [ ] 7.2 Move Plane OS-225 to Done.
