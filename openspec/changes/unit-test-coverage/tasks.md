# Tasks: unit-test-coverage

## 1. SpecParsingService Edge Case Tests

- [x] 1.1 Add test: SHALL NOT keyword extraction (first keyword in requirement is captured)
- [x] 1.2 Add test: SHOULD NOT keyword extraction
- [x] 1.3 Add test: multiple scenarios per requirement (all scenarios captured with clauses)
- [x] 1.4 Add test: AND clauses in scenarios (all 4 clause types: GIVEN, WHEN, THEN, AND)
- [x] 1.5 Add test: requirement without any RFC 2119 keyword (keyword is null)
- [x] 1.6 Add test: title-only content (no requirements, title parsed correctly)
- [x] 1.7 Add test: body text extraction (body stops before scenario marker)
- [x] 1.8 Add test: multiple requirements with mixed keywords
- [x] 1.9 Run test suite — all SpecParsingService tests pass

## 2. BuiltInValidator Spec Validation Tests

- [x] 2.1 Create test fixture: valid spec file (title + requirement + keyword + scenario)
- [x] 2.2 Create test fixture: spec missing title
- [x] 2.3 Create test fixture: spec missing requirements
- [x] 2.4 Create test fixture: requirement without RFC 2119 keywords
- [x] 2.5 Create test fixture: scenario without GIVEN/WHEN/THEN clauses
- [x] 2.6 Write integration test: valid spec produces no errors
- [x] 2.7 Write integration test: missing title triggers spec-title-required ERROR
- [x] 2.8 Write integration test: missing requirement triggers spec-requirement-required ERROR
- [x] 2.9 Write integration test: missing keyword triggers spec-rfc-keywords WARNING
- [x] 2.10 Write integration test: empty scenario triggers spec-scenario-clauses WARNING
- [x] 2.11 Run test suite — all spec validation tests pass

## 3. BuiltInValidator Config Validation Tests

- [x] 3.1 Create test fixture: valid config.yaml (schema + profile)
- [x] 3.2 Create test fixture: config missing schema field
- [x] 3.3 Create test fixture: config with invalid schema value
- [x] 3.4 Create test fixture: config missing profile
- [x] 3.5 Write integration test: valid config produces no errors
- [x] 3.6 Write integration test: missing schema triggers config-schema-required ERROR
- [x] 3.7 Write integration test: invalid schema triggers config-schema-invalid WARNING
- [x] 3.8 Write integration test: missing profile triggers config-profile-recommended WARNING
- [x] 3.9 Run test suite — all config validation tests pass

## 4. BuiltInValidator Change Validation Tests

- [x] 4.1 Create test fixture: change with proposal.md present
- [x] 4.2 Create test fixture: change without proposal.md
- [x] 4.3 Create test fixture: delta spec missing ADDED/MODIFIED/REMOVED sections
- [x] 4.4 Write integration test: valid change produces no errors
- [x] 4.5 Write integration test: missing proposal triggers change-proposal-required ERROR
- [x] 4.6 Write integration test: missing artifacts trigger change-artifact-missing WARNING
- [x] 4.7 Write integration test: delta spec without sections triggers delta-spec-sections WARNING
- [x] 4.8 Run test suite — all change validation tests pass

## 5. Final Validation

- [x] 5.1 Run full test suite: `./gradlew clean build test` — all green
- [x] 5.2 Verify no existing tests regressed
