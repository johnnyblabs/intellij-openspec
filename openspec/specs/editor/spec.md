# Editor

## Purpose
Syntax highlighting, annotations, and gutter icons for spec files.

## Requirements

### Requirement: RFC 2119 Keyword Highlighting

The plugin SHALL highlight RFC 2119 keywords (SHALL, SHOULD, MAY, SHALL NOT, SHOULD NOT) in spec files.

#### Scenario: Keyword in requirement body
- GIVEN a spec file containing the word "SHALL" in a requirement body
- WHEN the file is displayed in the editor
- THEN "SHALL" SHALL be highlighted with a distinct color

### Requirement: Scenario Keyword Highlighting

The plugin SHALL highlight scenario keywords (GIVEN, WHEN, THEN, AND) in spec files.

#### Scenario: Scenario clause keywords
- GIVEN a spec file containing a scenario with GIVEN, WHEN, THEN clauses
- WHEN the file is displayed in the editor
- THEN each keyword SHALL be highlighted with a distinct color

### Requirement: Requirement Gutter Icons

The plugin SHALL display gutter icons next to requirement headings for quick navigation when available.

#### Scenario: Gutter icon display
- GIVEN a spec file with `### Requirement:` headings
- WHEN the file is displayed in the editor
- THEN a gutter icon SHALL appear next to each requirement heading
