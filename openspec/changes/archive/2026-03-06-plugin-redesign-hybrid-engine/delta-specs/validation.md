# Delta Spec: Validation Engine

## ADDED

### ValidationResult
- Record: passed, List<ValidationIssue>, source ("built-in"/"cli"/"merged")
- Static merge() method combines built-in and CLI results
- Helper methods: errorCount(), warningCount()

### ValidationIssue
- Record: severity (ERROR/WARNING/INFO), filePath, line, message, rule

### BuiltInValidator
- Project service implementing spec, change, and config validation
- Spec rules: title required, requirement sections required, RFC 2119 keywords, GIVEN/WHEN/THEN in scenarios
- Change rules: proposal.md required, version-aware artifact requirements, delta-spec section validation
- Config rules: schema field required, valid schema values, profile recommended
- Strictness mode: warnings become errors when enabled in settings

### VersionSupport
- Enum: V1_0, V1_1, V1_2 with version-specific required fields, artifacts, and schemas
- fromString() maps version strings to enum values, defaults to latest

### CliOutputParser
- Parses CLI validate output (JSON and text formats) into ValidationResult
- parseListOutput() for structured list parsing
