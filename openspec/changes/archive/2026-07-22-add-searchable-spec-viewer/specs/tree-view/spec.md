## MODIFIED Requirements

### Requirement: Search and filtering

The plugin SHALL provide real-time tree filtering by case-insensitive substring with auto-expand and keyboard shortcut access. Filtering SHALL match a node's label and, for spec content, SHALL also match requirement body text and scenario text so that a term occurring only inside a requirement's prose surfaces its spec and requirement nodes. Content matching SHALL be performed during the off-UI-thread model build over the local OpenSpec files, without persisting a search index.

#### Scenario: Filter behavior
- **WHEN** the user types in the search field
- **THEN** the tree SHALL filter in real-time, auto-expand matches, and restore on clear

#### Scenario: Filter matches requirement body text
- **WHEN** the user types a term that appears in a requirement's body or scenario text but not in any node label
- **THEN** the tree SHALL surface that requirement (and its spec) as a match
