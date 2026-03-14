### Requirement: Coverage scanning

The plugin SHALL provide a SpecCoverageService that scans Java source files for `@spec` references and cross-references them against parsed spec requirements.

#### Scenario: Scan finds matching references
- **WHEN** a Java file contains `// @spec plugin-core:Project Detection`
- **AND** `openspec/specs/plugin-core/spec.md` contains a requirement named "Project Detection"
- **THEN** the coverage result SHALL mark that requirement as covered with the referencing file path

#### Scenario: Requirement with no references
- **WHEN** no Java file in the project contains an `@spec` reference matching a requirement
- **THEN** the coverage result SHALL mark that requirement as uncovered

#### Scenario: Reference to unknown requirement
- **WHEN** a Java file contains `// @spec domain:NonExistent Requirement`
- **AND** no spec defines that requirement
- **THEN** the reference SHALL be ignored in coverage results

### Requirement: Coverage panel display

The plugin SHALL display a "Coverage" tab in the OpenSpec tool window showing per-domain and per-requirement coverage status.

#### Scenario: Tree structure
- **WHEN** the Coverage tab is opened
- **THEN** it SHALL display a tree with domain nodes as parents and requirement nodes as children

#### Scenario: Covered requirement display
- **WHEN** a requirement has `@spec` references in code
- **THEN** its tree node SHALL show a covered indicator and the referencing file name

#### Scenario: Uncovered requirement display
- **WHEN** a requirement has no `@spec` references in code
- **THEN** its tree node SHALL show an uncovered indicator

#### Scenario: Domain coverage summary
- **WHEN** a domain has requirements with mixed coverage
- **THEN** the domain node label SHALL show the count of covered vs total requirements

#### Scenario: Overall coverage summary
- **WHEN** the coverage tree is populated
- **THEN** the root node SHALL show the total number of requirements and overall coverage percentage

### Requirement: Coverage navigation

Clicking a coverage tree node SHALL navigate to the relevant file.

#### Scenario: Click covered requirement
- **WHEN** the user double-clicks a covered requirement node
- **THEN** the spec file SHALL open in the editor

#### Scenario: Click uncovered requirement
- **WHEN** the user double-clicks an uncovered requirement node
- **THEN** the spec file SHALL open in the editor

### Requirement: Coverage refresh

The coverage panel SHALL provide a manual refresh action.

#### Scenario: Refresh button
- **WHEN** the user clicks the Refresh button in the coverage panel toolbar
- **THEN** the panel SHALL rescan all source files and update the tree

#### Scenario: Scanning state
- **WHEN** a coverage scan is in progress
- **THEN** the panel SHALL display a "Scanning..." indicator
