# Delta — validation

## MODIFIED Requirements

### Requirement: Spec format validation

The plugin SHALL validate spec files for structural completeness: title heading, requirement blocks, RFC 2119 keywords, and scenario format. Requirement headers (`### Requirement:`) SHALL be recognized case-insensitively on the header token, matching OpenSpec CLI 1.4+ parsing. RFC 2119 keyword presence SHALL be evaluated against the requirement body; a keyword appearing only in the requirement header SHALL produce a targeted diagnostic directing the author to move the keyword onto a body line, and the inspection SHALL offer a quick-fix for it. The inspection SHALL guard against zero-length PSI elements and invalid offsets before creating problem descriptors.

#### Scenario: Missing title heading
- **WHEN** a spec file has no `# Title` heading
- **THEN** the validator SHALL report an ERROR with code `spec-title-required`

#### Scenario: Missing requirement block
- **WHEN** a spec file has no `### Requirement:` section
- **THEN** the validator SHALL report an ERROR with code `spec-requirement-required`

#### Scenario: Requirement header with non-canonical casing
- **WHEN** a spec file contains a requirement header written as `### requirement:` or `### REQUIREMENT:` (any casing of the header token)
- **THEN** the validator and inspections SHALL recognize it as a requirement block, exactly as the OpenSpec CLI 1.4+ parser does

#### Scenario: Missing RFC 2119 keywords
- **WHEN** a requirement's body contains no RFC 2119 keywords (SHALL, SHOULD, MAY, SHALL NOT, SHOULD NOT) and its header contains none either
- **THEN** the validator SHALL report an ERROR with code `spec-rfc-keywords`

#### Scenario: RFC 2119 keyword only in the header
- **WHEN** a requirement's header line contains an RFC 2119 keyword but its body contains none
- **THEN** the validator SHALL report an ERROR with code `spec-rfc-keyword-in-header` whose message directs the author to move the keyword onto the requirement body line, mirroring the OpenSpec CLI 1.4+ validation hint

#### Scenario: Quick-fix for keyword-in-header
- **WHEN** the inspection reports `spec-rfc-keyword-in-header` on a requirement
- **THEN** it SHALL offer a quick-fix that inserts or rewrites a body line carrying the keyword via a deterministic text edit inside a `WriteAction`, leaving the header text unchanged

#### Scenario: Missing scenario
- **WHEN** a requirement block has no `#### Scenario:` section
- **THEN** the validator SHALL report an ERROR with code `spec-scenario-required`

#### Scenario: Scenario missing clauses
- **WHEN** a scenario block is missing a WHEN clause, a THEN clause, or both
- **THEN** the validator SHALL report an ERROR with code `spec-scenario-clauses`

#### Scenario: Invalid offset from text search
- **WHEN** `String.indexOf()` returns `-1` while locating a requirement heading
- **THEN** the inspection SHALL skip that requirement rather than passing an invalid offset to `findElementAt()`
