## ADDED Requirements

### Requirement: Spec reference comment detection

The plugin SHALL detect `// @spec <domain>:<requirement-name>` comments in Java source files and display a gutter icon on the corresponding line.

#### Scenario: Single @spec comment
- **WHEN** a Java file contains `// @spec plugin-core:Project Detection`
- **THEN** a spec gutter icon SHALL appear on that line

#### Scenario: Comment with surrounding code
- **WHEN** a `// @spec` comment appears on the line before a method or class declaration
- **THEN** the gutter icon SHALL appear on the comment line, not the declaration line

#### Scenario: Non-matching comments ignored
- **WHEN** a Java file contains regular comments without the `@spec` prefix
- **THEN** no spec gutter icon SHALL appear on those lines

#### Scenario: Non-OpenSpec projects
- **WHEN** a Java file contains `@spec` comments but the project is not an OpenSpec project
- **THEN** no spec gutter icons SHALL appear

### Requirement: Gutter icon tooltip

The gutter icon SHALL display a tooltip showing the spec domain and requirement name.

#### Scenario: Tooltip content
- **WHEN** the user hovers over a spec gutter icon for `// @spec plugin-core:Config Parsing`
- **THEN** the tooltip SHALL display "Spec: plugin-core — Config Parsing"

### Requirement: Click-to-navigate

Clicking the gutter icon SHALL open the corresponding spec file in the editor.

#### Scenario: Navigate to existing spec
- **WHEN** the user clicks a spec gutter icon for `// @spec plugin-core:Project Detection`
- **THEN** the file `openspec/specs/plugin-core/spec.md` SHALL open in the editor

#### Scenario: Spec file does not exist
- **WHEN** the user clicks a spec gutter icon referencing a domain with no spec file
- **THEN** no navigation SHALL occur and no error SHALL be shown
