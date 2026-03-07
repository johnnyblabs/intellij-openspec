## Why

SpecParsingService and BuiltInValidator are two core services with insufficient test coverage. SpecParsingService has basic happy-path tests but no edge case coverage (multi-keyword requirements, AND clauses, malformed input). BuiltInValidator has 0% test coverage — none of its spec validation, change validation, or config validation rules are tested. This is a risk for v0.1.0 as any future changes could introduce regressions undetected.

## What Changes

- Add comprehensive unit tests for `SpecParsingService.parseSpecContent()` covering edge cases: multiple keywords per requirement (SHALL NOT, SHOULD NOT), AND clauses in scenarios, multiple scenarios per requirement, title-only content, requirements without keywords, and malformed markdown
- Add comprehensive unit tests for `BuiltInValidator` covering all validation rules: spec validation (title required, requirement required, RFC 2119 keywords, scenario clauses), change validation (proposal required, artifact missing, delta spec sections), and config validation (schema required, schema invalid, profile recommended)
- Add test fixtures as needed to support the new test cases

## Capabilities

### New Capabilities
<!-- None — this is a testing change, no new user-facing capabilities -->

### Modified Capabilities
<!-- No spec-level requirement changes — testing validates existing behavior -->

## Impact

- `src/test/java/com/johnnyb/openspec/SpecParsingServiceTest.java` — expanded with edge case tests
- `src/test/java/com/johnnyb/openspec/validation/BuiltInValidatorTest.java` — new test class
- `src/test/resources/` — new test fixtures for validation scenarios
- No production code changes
- Covers Forgejo issues #59 and #60
