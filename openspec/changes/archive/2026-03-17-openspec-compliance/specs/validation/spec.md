## MODIFIED Requirements

### Requirement: Spec format validation

The plugin SHALL validate spec files for title headings, requirement structure, RFC 2119 keywords, and Given-When-Then scenario format. RFC 2119 keyword presence in each requirement SHALL be an ERROR (not a warning). Each scenario block SHALL contain WHEN and THEN clauses as an ERROR condition.

#### Scenario: Validation rules
- **WHEN** validation runs on a spec file
- **THEN** it SHALL check for `# Title` heading, `### Requirement:` headings, RFC 2119 keywords, and scenario structure

#### Scenario: Missing RFC 2119 keyword is an error
- **WHEN** a `### Requirement:` block does not contain at least one RFC 2119 keyword (SHALL, MUST, SHOULD, MAY)
- **THEN** the validator SHALL report an ERROR with the message identifying the requirement name and stating that RFC 2119 keywords are required

#### Scenario: Missing scenario clauses is an error
- **WHEN** a `#### Scenario:` block does not contain both WHEN and THEN clauses
- **THEN** the validator SHALL report an ERROR with the message identifying the scenario name and the missing clause(s)

## ADDED Requirements

### Requirement: Delta spec structural validation
The plugin SHALL validate that delta spec operation sections contain structurally complete requirement blocks. Each `### Requirement:` block under `## ADDED Requirements` or `## MODIFIED Requirements` SHALL contain a description and at least one `#### Scenario:` block with WHEN and THEN clauses. Each `### Requirement:` block under `## REMOVED Requirements` SHALL contain `**Reason**` and `**Migration**` fields. Violations SHALL be reported as ERRORs.

#### Scenario: ADDED requirement missing scenario
- **WHEN** a delta spec has a `### Requirement:` block under `## ADDED Requirements` with no `#### Scenario:` block
- **THEN** the validator SHALL report an ERROR identifying the requirement name and stating that at least one scenario is required

#### Scenario: MODIFIED requirement missing scenario
- **WHEN** a delta spec has a `### Requirement:` block under `## MODIFIED Requirements` with no `#### Scenario:` block
- **THEN** the validator SHALL report an ERROR identifying the requirement name and stating that MODIFIED requirements must include the full updated content with scenarios

#### Scenario: REMOVED requirement missing fields
- **WHEN** a delta spec has a `### Requirement:` block under `## REMOVED Requirements` without both `**Reason**` and `**Migration**` fields
- **THEN** the validator SHALL report an ERROR identifying the requirement name and the missing field(s)

#### Scenario: Valid delta spec passes
- **WHEN** a delta spec has structurally complete requirement blocks in all operation sections
- **THEN** the validator SHALL report no errors for delta spec structure

### Requirement: Requirement must have at least one scenario
The plugin SHALL validate that every `### Requirement:` block in a spec file (both main specs and delta specs) contains at least one `#### Scenario:` block. A requirement with no scenarios SHALL be reported as an ERROR.

#### Scenario: Requirement without scenario
- **WHEN** a spec file contains a `### Requirement:` block with no `#### Scenario:` blocks before the next `### Requirement:` or end of file
- **THEN** the validator SHALL report an ERROR stating the requirement name must have at least one scenario

#### Scenario: Requirement with scenario passes
- **WHEN** a spec file contains a `### Requirement:` block followed by at least one `#### Scenario:` block
- **THEN** the validator SHALL not report a scenario-related error for that requirement
