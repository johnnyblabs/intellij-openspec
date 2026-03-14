# Editor

## Purpose
In-editor features: syntax highlighting, gutter markers, spec coverage, search, and diff viewer.

## Requirements

### Requirement: Syntax highlighting

The plugin SHALL highlight RFC 2119 keywords (SHALL, MUST, etc.) and scenario keywords (GIVEN, WHEN, THEN, AND) in spec files.

#### Scenario: Keyword highlighting
- **WHEN** a spec file is opened in the editor
- **THEN** RFC 2119 and scenario keywords SHALL be visually highlighted

### Requirement: Spec gutter markers

The plugin SHALL detect `// @spec <domain>:<requirement>` comments in Java files and display gutter icons with tooltip and click-to-navigate.

#### Scenario: Gutter navigation
- **WHEN** a Java file contains `@spec` comments
- **THEN** gutter icons SHALL appear with tooltips showing the referenced spec, and clicking SHALL navigate to the spec file

### Requirement: Spec coverage panel

The plugin SHALL scan Java files for `@spec` references, cross-reference against specs, and display a coverage panel showing referenced vs unreferenced requirements.

#### Scenario: Coverage display
- **WHEN** the Coverage tab is selected
- **THEN** it SHALL show a tree of domains/requirements with referenced (green) and unreferenced (gray) indicators

### Requirement: Delta spec diff viewer

The plugin SHALL provide a "Preview Diff" action on delta spec nodes showing a side-by-side diff against the corresponding main spec.

#### Scenario: Diff preview
- **WHEN** the user right-clicks a delta spec and selects "Preview Diff"
- **THEN** a diff viewer SHALL open comparing the delta spec against its main spec
