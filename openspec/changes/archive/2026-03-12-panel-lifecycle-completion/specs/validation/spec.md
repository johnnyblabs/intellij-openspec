## ADDED Requirements

### Requirement: Per-change validation
The BuiltInValidator SHALL provide a method to validate a single change by name, returning validation results scoped to that change only.

#### Scenario: Validate a single active change
- **WHEN** `validateChange(changeName)` is called with a valid active change name
- **THEN** it SHALL return a `ValidationResult` containing only issues for that change
- **AND** it SHALL check required artifacts, delta spec format, and proposal existence

#### Scenario: Validate a non-existent change
- **WHEN** `validateChange(changeName)` is called with a name that doesn't match any active change
- **THEN** it SHALL return a passing `ValidationResult` with no issues
