# Design: implement-tests

## Context

The `implement-tests` change exists to close testing gaps in the plugin, with the proposal explicitly calling out settings tool detection behavior as likely under-tested. The current proposal is intentionally lightweight and does not yet define exact capabilities or impacted modules, so this design sets a practical, low-risk direction for adding tests before implementation work starts.

Current constraints and stakeholders:
- The plugin is an IntelliJ Platform plugin, so some behavior depends on IDE/project services and can require platform-aware test patterns.
- Existing tests already mix unit and integration styles, so new coverage should fit that structure.
- Primary stakeholders are maintainers who need regression protection and contributors who need clear test expectations.

This design treats test additions as behavior-preserving work: increase confidence without changing runtime logic.

## Goals / Non-Goals

**Goals:**
- Add reliable automated tests for settings tool detection behavior.
- Define a repeatable strategy for where to place unit vs integration tests.
- Cover both success and failure paths (detected, not detected, invalid/missing config).
- Keep tests deterministic and fast enough for regular CI usage.
- Improve maintainability by using reusable fixtures/helpers for settings-related scenarios.

**Non-Goals:**
- Redesigning settings architecture or detection algorithms.
- Expanding this change into broad feature refactors.
- Achieving complete code coverage across the entire plugin in one change.
- Introducing new external test frameworks unless current tooling is insufficient.

## Decisions

### Decision 1: Use a two-layer test strategy (unit-first, targeted integration)

**Choice:**
Write focused unit tests for detection logic first, then add targeted IntelliJ integration tests only where project/service wiring must be validated.

**Rationale:**
- Unit tests provide fast feedback and isolate edge cases.
- Integration tests verify plugin wiring and IDE lifecycle interactions that unit tests cannot prove.
- This balances confidence and CI runtime.

**Alternatives considered:**
- Integration-only: rejected due to slower execution and harder diagnosis.
- Unit-only: rejected because service registration and IDE environment behavior can regress silently.

### Decision 2: Define canonical detection scenarios and convert each to test cases

**Choice:**
Model tests around explicit scenarios for settings detection outcomes:
- tool detected from expected configuration
- no tool detected when config is absent
- invalid configuration handled gracefully
- ambiguous/multiple tool hints resolved consistently

**Rationale:**
- Scenario-driven tests map directly to spec requirements and prevent missing edge paths.
- Makes failures easier to interpret by behavior instead of internals.

**Alternatives considered:**
- Assertion-only coverage without scenario mapping: rejected as harder to audit for completeness.

### Decision 3: Standardize fixtures under existing test resources

**Choice:**
Store reusable settings fixtures in test resources and load them from tests instead of embedding large inline strings.

**Rationale:**
- Improves readability and reduces duplication.
- Keeps fixture updates centralized when format assumptions evolve.

**Alternatives considered:**
- Inline fixtures in each test class: rejected due to repetition and drift.

### Decision 4: Gate this change on regression-safe CI validation

**Choice:**
Require all newly added tests to pass in existing Gradle test execution without special local-only setup.

**Rationale:**
- Ensures tests are portable and enforceable in normal workflows.
- Prevents "works locally" only test additions.

**Alternatives considered:**
- Separate optional test task: rejected because optional checks do not protect mainline stability.

## Risks / Trade-offs

- [Flaky integration tests due to IntelliJ lifecycle timing] -> Mitigation: keep integration scope small, use stable platform test patterns, and avoid asynchronous timing dependencies where possible.
- [Over-mocking in unit tests hides real wiring bugs] -> Mitigation: pair critical unit scenarios with at least one integration test per behavior family.
- [Fixture drift from real project layouts] -> Mitigation: base fixtures on representative sample configs and review fixtures during related feature changes.
- [Scope creep into unrelated test debt] -> Mitigation: constrain this change to settings detection and directly adjacent behaviors unless proposal/specs are expanded.

## Migration Plan

1. Baseline current test behavior and identify the exact settings detection entry points to cover.
2. Add/organize shared test fixtures for valid, invalid, and missing settings cases.
3. Implement unit tests for detection decision logic and error handling.
4. Add minimal integration tests for service wiring and end-to-end detection invocation.
5. Run full test suite and resolve nondeterminism before merge.
6. Document added coverage and any intentionally deferred scenarios in `tasks.md` or follow-up change notes.

Rollback strategy:
- If failures emerge in CI, revert only the newly introduced test classes/fixtures in this change first.
- If tests expose real defects, keep tests and patch production behavior in a follow-up commit.

## Open Questions

- Which exact service/class is the canonical owner of "settings tool detect" behavior for test ownership?
- Do we need to treat AI tool classification outputs (CLI vs IDE_PANEL) as part of this change's acceptance criteria?
- What minimum integration test matrix is required to avoid slowing CI while still protecting plugin wiring?
- Should this change include coverage targets (for example, branch coverage for detection logic), or stay purely scenario-complete?
