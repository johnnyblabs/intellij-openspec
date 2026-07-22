# Nested List Scenario

## Purpose
A capability whose single scenario uses nested bullet lists under its clauses.

## Requirements

### Requirement: Nested clauses
The system SHALL handle nested lists.

#### Scenario: Deeply nested
- **WHEN** a request arrives
  - with a nested detail
  - and another nested detail
- **THEN** it is handled
  - producing a nested outcome
