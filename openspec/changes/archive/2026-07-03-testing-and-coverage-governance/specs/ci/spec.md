## ADDED Requirements

### Requirement: Test coverage regression floor

The build SHALL enforce a JaCoCo coverage regression floor, wired into `check` so it runs in CI. The floor SHALL cover at least the **instruction**, **line**, and **branch** counters, and each minimum SHALL be set just below the recorded coverage baseline so that a change reducing coverage below the floor fails the build. The coverage baseline SHALL be recorded (measured value and date) alongside the floor. The floor SHALL be ratcheted **upward** as coverage improves and SHALL NOT be lowered without a recorded justification.

#### Scenario: Regression below the floor fails the build
- **WHEN** a change reduces measured coverage for a governed counter below its configured minimum
- **THEN** `jacocoTestCoverageVerification` SHALL fail, and because it is wired into `check`, `./gradlew build` (and CI) SHALL fail

#### Scenario: Branch coverage is governed
- **WHEN** the coverage floor is evaluated
- **THEN** it SHALL include a branch-coverage minimum in addition to instruction and line

#### Scenario: The floor is not silently lowered
- **WHEN** a change lowers a coverage minimum
- **THEN** the change SHALL record a justification; lowering a floor to make an unrelated change pass is not permitted

### Requirement: Testable functionality is unit-tested

Functionality that can be exercised without the running IDE platform — parsers, resolvers, version comparisons, action-gating logic, path/format handling, and similar pure logic — SHALL have unit tests that assert real behavior, such that each test fails if the behavior it covers is broken. A change that adds or materially alters such functionality SHALL add or update the corresponding tests in the same change. Tests that parse output the plugin does not control SHALL be contract-tested against captured real output rather than hand-authored shapes.

#### Scenario: New testable logic ships with tests
- **WHEN** a change adds functionality that can be unit-tested without the IDE platform
- **THEN** the change SHALL include unit tests asserting that functionality's real behavior, and each SHALL fail if the covered behavior regresses

#### Scenario: External-output parsers are contract-tested
- **WHEN** code parses output from an external tool (a CLI `--json`, an on-disk format, an API response)
- **THEN** its tests SHALL run against captured real output committed as a fixture, not against a hand-authored approximation
