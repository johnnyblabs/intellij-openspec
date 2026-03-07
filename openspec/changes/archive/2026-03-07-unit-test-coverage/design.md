## Context

SpecParsingService has 5 unit tests covering basic parsing (title, requirements, scenarios, empty content, no-scenario content). BuiltInValidator has 0 unit tests — only `ValidationResult` and `ValidationIssue` records are tested. The validator depends heavily on IntelliJ project services (ChangeService, ConfigService, OpenSpecSettings, VirtualFile), making it harder to unit test without mocking or the platform test framework.

Existing test patterns:
- Unit tests: JUnit 5, direct instantiation with `null` Project, resource loading via classpath
- Integration tests: `BasePlatformTestCase` with fixture copying, project service access
- No mocking framework (Mockito) in dependencies
- Test fixtures in `src/test/resources/openspec/` and `src/test/resources/testProject/`

## Goals / Non-Goals

**Goals:**
1. Cover SpecParsingService edge cases that the current 5 tests miss
2. Test all BuiltInValidator validation rules (spec, change, config)
3. Maintain existing test patterns (no new dependencies)

**Non-Goals:**
- Adding Mockito or other mocking frameworks
- Testing private methods directly
- Integration testing of BuiltInValidator (requires full platform — defer to integration tests)
- 100% line coverage — focus on behavioral coverage of validation rules

## Decisions

### Decision 1: BuiltInValidator testing strategy

**Choice:** Integration tests using `BasePlatformTestCase` with test fixture files.

**Rationale:** BuiltInValidator calls `project.getService()`, `OpenSpecFileUtil.getSpecsDir()`, and `OpenSpecSettings.getInstance()` — all require a live IntelliJ project. Without Mockito, the only way to test is via the platform test framework which provides a real project. The existing `OpenSpecIntegrationTestBase` already handles fixture setup.

**Alternative rejected:** Adding Mockito — introduces a new dependency for one test class. Not worth the complexity.

### Decision 2: Test fixture strategy

**Choice:** Create minimal spec/config/change fixtures that trigger specific validation rules. Each fixture targets one rule to keep tests isolated.

**Rationale:** One fixture per rule makes failures easy to diagnose. Shared fixtures with multiple issues make it hard to tell which rule is being tested.

### Decision 3: SpecParsingService test approach

**Choice:** Expand existing `SpecParsingServiceTest` with inline content strings (not fixture files) for edge cases.

**Rationale:** Edge cases are small, specific inputs. Inline strings in the test are more readable than external fixture files for cases like "title only" or "requirement without keywords." The existing test already uses this pattern for the no-scenario test.

## Risks / Trade-offs

**Risk:** Integration tests are slower than unit tests (platform setup overhead).
→ Mitigation: Only BuiltInValidator needs integration tests. SpecParsingService tests remain fast unit tests.

**Risk:** Test fixtures may drift from production OpenSpec file format.
→ Mitigation: Fixtures are minimal and match the regex patterns the validator actually uses. They test the validator's logic, not file format compliance.
