# Editor

## Purpose
In-editor features: syntax highlighting and a delta spec diff viewer.

## Requirements

### Requirement: Syntax highlighting

The plugin SHALL highlight RFC 2119 keywords (SHALL, MUST, etc.) and scenario keywords (GIVEN, WHEN, THEN, AND) in spec files.

#### Scenario: Keyword highlighting
- **WHEN** a spec file is opened in the editor
- **THEN** RFC 2119 and scenario keywords SHALL be visually highlighted

### Requirement: Delta spec diff viewer

The plugin SHALL provide a "Preview Diff" action on delta spec nodes showing a side-by-side diff against the corresponding main spec.

#### Scenario: Diff preview
- **WHEN** the user right-clicks a delta spec and selects "Preview Diff"
- **THEN** a diff viewer SHALL open comparing the delta spec against its main spec

