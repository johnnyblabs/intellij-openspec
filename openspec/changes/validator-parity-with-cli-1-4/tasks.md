# Tasks — Validator parity with OpenSpec CLI 1.4

## 1. Shared header pattern

- [ ] 1.1 Introduce a single case-insensitive requirement-header pattern (shared constant) and adopt it in `BuiltInValidator`, `DeltaSpecInspection`, `SpecFormatInspection`, and `SpecParsingService`
- [ ] 1.2 Adopt case-insensitive header matching in `SpecSyncService` block location (MODIFIED/REMOVED/RENAMED lookups) while keeping written headers in canonical `### Requirement:` casing
- [ ] 1.3 Audit remaining header consumers (`SpecAnnotator`, `SpecTreeModel`, `OpenSpecLineMarkerProvider`, `VerificationService`, `ExploreContextService`) and switch any header-token matching to the shared pattern

## 2. Keyword-placement diagnostic

- [ ] 2.1 Split the RFC 2119 keyword check in `BuiltInValidator` to evaluate the requirement body: keep `spec-rfc-keywords` for keyword-nowhere, add `spec-rfc-keyword-in-header` (ERROR) with the "move the keyword onto the requirement body line" message for header-only keyword
- [ ] 2.2 Mirror the split in `SpecFormatInspection`/`DeltaSpecInspection` problem descriptors
- [ ] 2.3 Implement the deterministic quick-fix for `spec-rfc-keyword-in-header` (insert/rewrite body line inside a `WriteAction`; header untouched)

## 3. Tests

- [ ] 3.1 Unit tests for the shared pattern: canonical, lowercase, uppercase, mixed-case headers recognized; line-anchoring preserved (no mid-line matches)
- [ ] 3.2 Validator tests: keyword-in-body passes; header-only keyword yields `spec-rfc-keyword-in-header`; keyword-nowhere still yields `spec-rfc-keywords`
- [ ] 3.3 Inspection test: quick-fix produces the expected text edit and resolves the diagnostic
- [ ] 3.4 Sync tests: MODIFIED/REMOVED/RENAMED operations resolve a main-spec block whose header uses non-canonical casing; rewritten headers come out canonical
- [ ] 3.5 Tree-view/parsing test: `SpecParsingService` lists requirements from a spec using lowercase headers

## 4. Documentation

- [ ] 4.1 Flip the two 1.4.0 rows (case-insensitive headers, validation hint) in `docs/openspec-support.md` from Partial to supported, and update the `docs/cli-versions/1.4.md` delta table "Plugin supported?" cells
- [ ] 4.2 Update `docs/feature-reference.md` validation section for the new diagnostic + quick-fix; CHANGELOG entry under Unreleased
