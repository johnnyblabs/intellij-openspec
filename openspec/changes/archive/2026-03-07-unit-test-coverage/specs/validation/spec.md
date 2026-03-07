## MODIFIED Requirements

### Requirement: Test coverage for validation rules

The validation subsystem SHALL have unit test coverage for all built-in validation rules.

#### Scenario: Spec validation rules are tested
- GIVEN the BuiltInValidator spec validation logic
- WHEN tests execute against spec fixtures with known issues
- THEN each rule (title-required, requirement-required, rfc-keywords, scenario-clauses) SHALL be individually verified

#### Scenario: Change validation rules are tested
- GIVEN the BuiltInValidator change validation logic
- WHEN tests execute against change fixtures with known issues
- THEN each rule (proposal-required, artifact-missing, delta-spec-sections) SHALL be individually verified

#### Scenario: Config validation rules are tested
- GIVEN the BuiltInValidator config validation logic
- WHEN tests execute against config fixtures with known issues
- THEN each rule (config-missing, schema-required, schema-invalid, profile-recommended) SHALL be individually verified

### Requirement: Test coverage for spec parsing edge cases

The spec parsing service SHALL have test coverage for edge cases beyond basic happy-path parsing.

#### Scenario: Multi-keyword requirements are parsed
- GIVEN a spec with SHALL NOT and SHOULD NOT keywords
- WHEN the spec is parsed
- THEN the first keyword in the requirement section SHALL be extracted

#### Scenario: AND clauses in scenarios are parsed
- GIVEN a spec scenario with AND clauses
- WHEN the spec is parsed
- THEN all clauses including AND SHALL be captured

#### Scenario: Multiple scenarios per requirement are parsed
- GIVEN a requirement with multiple scenarios
- WHEN the spec is parsed
- THEN all scenarios SHALL be captured with their respective clauses
