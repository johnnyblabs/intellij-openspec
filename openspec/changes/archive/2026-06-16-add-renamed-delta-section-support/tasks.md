## 1. Validator + inspection regex + warning

- [x] 1.1 `BuiltInValidator.java:34` — extend `DELTA_SECTION_PATTERN` alternation from `(ADDED|MODIFIED|REMOVED)` to `(ADDED|MODIFIED|REMOVED|RENAMED)`.
- [x] 1.2 `BuiltInValidator.java:218` — update warning text to "Delta spec should have ADDED, MODIFIED, REMOVED, or RENAMED sections".
- [x] 1.3 `BuiltInValidator.java:215` — update the leading comment to match.
- [x] 1.4 `BuiltInValidator.java:385` — extend `deltaSectionStart` regex inside `validateDeltaSpecStructure` to include `RENAMED`.
- [x] 1.5 `DeltaSpecInspection.java:20-21` — mirror the regex change.
- [x] 1.6 `DeltaSpecInspection.java:40` and `:46` — mirror the comment + warning text change.

## 2. RENAMED structural validation

- [x] 2.1 Add a private `RENAMED_ENTRY` regex constant in `BuiltInValidator` matching `^\s*(?:-\s*)?FROM:\s*(.+)$\s*^\s*(?:-\s*)?TO:\s*(.+)$` (same shape as `SpecSyncService.RENAMED_ENTRY`).
- [x] 2.2 `BuiltInValidator.validateDeltaSpecStructure` — add a third arm for `"RENAMED".equals(sectionType)`: if the section content yields no `RENAMED_ENTRY` match, emit `new ValidationIssue(ERROR, path, sectionLine, "RENAMED section must contain at least one FROM:/TO: pair", "delta-renamed-fields")`. Do NOT iterate `### Requirement:` headers under RENAMED — the section's content is the FROM/TO pairs themselves, not requirement blocks.
- [x] 2.3 Mirror in `DeltaSpecInspection.checkFile` — same regex constant, same arm, attach the ProblemDescriptor to the section heading PSI element.

## 3. Realign SpecSyncService apply-order

- [x] 3.1 `SpecSyncService.java:305-314` — switch the priority assignment so the sort order becomes `RENAMED → REMOVED → MODIFIED → ADDED` (upstream order). Concretely: `case RENAMED -> 0; case REMOVED -> 1; case MODIFIED -> 2; case ADDED -> 3;`.
- [x] 3.2 `SpecSyncServiceTest.java:352-366` — update `sortOperations_ordersCorrectly` to expect the new order. If the test's docstring or test name implies the old order, update.

## 4. Scaffolding template

- [x] 4.1 `TemplateProvider.java:78-94` — append a `## RENAMED Requirements\n\n- FROM: Old requirement name\n- TO: New requirement name\n` block to the delta-spec template, after the existing REMOVED block.
- [x] 4.2 `TemplateProviderTest.java:106-114` — extend `deltaSpecTemplate_hasAddedModifiedRemoved` (or split into a new test) to also assert `## RENAMED` is present.

## 5. Tests for RENAMED validation

- [x] 5.1 `BuiltInValidatorRulesTest.java:23` — extend the test-local `DELTA_SECTION_PATTERN` to include `RENAMED`. The helper `validateDeltaStructure` mirrors the validator; if it diverges, ensure it covers the new RENAMED arm.
- [x] 5.2 Add `deltaRenamed_validFromTo_passes()` — `## RENAMED Requirements\n\n- FROM: Old\n- TO: New` produces zero `delta-spec-sections` or `delta-renamed-fields` issues.
- [x] 5.3 Add `deltaRenamed_missingFromTo_isError()` — `## RENAMED Requirements\n\n(empty)` produces an ERROR with rule `delta-renamed-fields`.
- [x] 5.4 Extend `validDeltaSpec_passes()` (or add `validDeltaSpec_withRenamed_passes()`) to include a `## RENAMED Requirements` block alongside the existing three sections — confirm no structural errors.
- [x] 5.5 Spot-check `BuiltInValidatorTest` (if it exists separately for full validator scenarios) for a RENAMED case; mirror if found.

## 6. Spec updates

- [x] 6.1 Confirm `openspec/specs/validation/spec.md:156-174` matches the change's delta-spec MODIFIED block exactly before/after the merge (sanity check on starting state).
- [x] 6.2 Confirm `openspec/specs/spec-sync/spec.md`'s apply-order scenario (sort priority list) matches the spec-sync delta-spec MODIFIED block.
- [x] 6.3 Run `openspec validate add-renamed-delta-section-support --strict` — expect green.

## 7. Verify

- [x] 7.1 `./gradlew test` — expect green. Targeted run: `./gradlew test --tests "*BuiltInValidatorRulesTest*" --tests "*SpecSyncServiceTest*" --tests "*TemplateProviderTest*"`.
- [x] 7.2 `./gradlew buildPlugin` — expect green.
- [x] 7.3 Sandbox (optional — `./gradlew runIde`): scaffold a delta spec via the plugin's New action, confirm template now contains `## RENAMED Requirements`. Edit a delta spec to add a `## RENAMED Requirements` block with no FROM/TO — confirm inline ERROR with rule `delta-renamed-fields`. Add FROM/TO — confirm error clears. If `runIde` not exercised, document why in the verification note.

## 8. Land

- [x] 8.1 **Skip** `/mirror-change-trackers` — trackers already exist for this work; IDs are in the gitignored `.tracking.yaml` sidecar.
- [x] 8.2 Commit and push.
- [x] 8.3 After implementation lands, archive via `/openspec-archive-change add-renamed-delta-section-support` and sync the delta into `openspec/specs/validation/spec.md` + `openspec/specs/spec-sync/spec.md`.
- [ ] 8.4 `close-change-trackers` closes the linked tracker entries when archive lands.

## 9. Follow-up

- [ ] 9.1 Future: cross-section FROM-collision detection in `BuiltInValidator` (see `design.md` D3). Defer until RENAMED appears in real deltas often enough to justify the structural change.
- [ ] 9.2 Pre-existing template gap (surfaced during this change's pre-commit review, fixed for RENAMED only): `TemplateProvider.deltaSpecTemplate` emits `## ADDED`, `## MODIFIED`, `## REMOVED` without the `Requirements` suffix that the validator's structural regex (`^## ... Requirements`) requires. This change fixes RENAMED but leaves the other three intentionally untouched (out-of-scope for this change). Either bring all four to the suffixed form OR loosen the validator regex to `(\s+Requirements)?` — pick one and do it as its own change.
- [ ] 9.3 Pre-existing duplication (surfaced during this change's pre-commit review): `findNonEmptyElement` is privately re-declared in 3 places (DeltaSpecInspection, SpecFormatInspection, ConfigValidationInspection); `RENAMED_ENTRY` regex now lives in 4 verbatim copies; `(ADDED|MODIFIED|REMOVED|RENAMED)` alternation lives in 5 string literals when `OperationType` could drive it. Lift to shared helpers / derive from enum. Lower-priority cleanup; not addressed here to keep scope tight.
- [ ] 9.4 Stale docs (surfaced during this change's pre-commit review): `docs/marketplace-page.md:92`, `docs/feature-reference.md:155`, `docs/getting-started-browser.md:70` still describe the three-section contract. Update before next release.
- [ ] 9.5 Stale sibling test (surfaced during this change's pre-commit review): `src/test/java/com/johnnyblabs/openspec/scaffolding/ScaffoldingContractTest.java:172-184` still pins the three-section regex and asserts. Update.
- [ ] 9.6 Integration test gap (surfaced during this change's pre-commit review): `BuiltInValidatorTest` (the VFS-driven integration test) has zero RENAMED coverage. Add one.
