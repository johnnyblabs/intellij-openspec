# Validation

## Domain
Real-time linting and inspection of spec files.

## Requirements

### Requirement: Spec Format Validation

The plugin SHALL validate that spec files contain properly formatted requirement headings.

**Keyword:** SHALL

#### Scenarios

**Scenario: Missing requirement heading**
- GIVEN a spec file without any `### Requirement:` headings
- WHEN the file is opened in the editor
- THEN the plugin SHALL display a warning inspection

### Requirement: RFC 2119 Keyword Check

The plugin SHOULD warn when requirements do not contain RFC 2119 keywords (SHALL, SHOULD, MAY).

**Keyword:** SHOULD

#### Scenarios

**Scenario: Requirement without keyword**
- GIVEN a requirement body that contains no RFC 2119 keywords
- WHEN the file is analyzed
- THEN the plugin SHOULD highlight the requirement with an info-level inspection

### Requirement: Scenario Structure Validation

The plugin SHALL validate that scenarios follow the Given-When-Then structure.

**Keyword:** SHALL

#### Scenarios

**Scenario: Scenario missing WHEN clause**
- GIVEN a scenario with GIVEN and THEN but no WHEN clause
- WHEN the file is analyzed
- THEN the plugin SHALL flag the scenario as incomplete
