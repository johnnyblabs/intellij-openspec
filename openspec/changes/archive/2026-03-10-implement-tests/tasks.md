## 1. Scope and Test Design

- [x] 1.1 Confirm target coverage boundaries for `delivery-preferences-ui` settings detection behavior.
- [x] 1.2 Map each scenario in `specs/delivery-preferences-ui/spec.md` to a planned automated test case.
- [x] 1.3 Identify the canonical detection and preferred-tool resolution entry points for test ownership.

## 2. Test Fixtures and Helpers

- [x] 2.1 Add reusable fixtures for detected tools, no tools detected, and invalid detection inputs.
- [x] 2.2 Add or update test helper utilities to load fixtures consistently across unit and integration tests.
- [x] 2.3 Validate fixture naming and placement under `src/test/resources` for long-term maintainability.

## 3. Unit Test Implementation

- [x] 3.1 Implement unit tests for successful preferred tool detection outcomes.
- [x] 3.2 Implement unit tests for no-tools and invalid-input safe fallback behavior.
- [x] 3.3 Implement unit tests for preferred-tool default and persisted-selection fallback logic.

## 4. Integration Test Implementation

- [x] 4.1 Add targeted IntelliJ integration tests for settings wiring that consumes detection results.
- [x] 4.2 Verify dropdown-facing labels and type indicators remain stable for detected tools.
- [x] 4.3 Verify "None" fallback behavior when no tools are detected in integration context.

## 5. Validation and Completion

- [x] 5.1 Run the full Gradle test suite and resolve flaky or nondeterministic failures.
- [x] 5.2 Document any intentionally deferred scenarios and associated follow-up test work.
- [x] 5.3 Confirm each completed task maps back to design decisions and spec scenarios.
