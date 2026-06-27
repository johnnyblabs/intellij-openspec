# editor (delta)

## MODIFIED Requirements

### Requirement: Spec gutter markers

The plugin SHALL detect `@spec <domain>:<requirement>` comments in source files of **any language** and display gutter icons with tooltip and click-to-navigate. Detection SHALL key off the language-neutral comment element rather than a specific language.

#### Scenario: Gutter navigation in a Java file
- **WHEN** a Java file contains an `@spec` comment
- **THEN** a gutter icon SHALL appear with a tooltip showing the referenced spec, and clicking it SHALL navigate to the spec file

#### Scenario: Gutter navigation in a non-Java file
- **WHEN** a file in another language (for example Kotlin, Go, Python, or TypeScript) contains an `@spec` comment
- **THEN** the same gutter icon, tooltip, and click-to-navigate behavior SHALL appear, identical to the Java case

### Requirement: Spec coverage panel

The plugin SHALL scan non-binary source files of **any language** under the project's content roots for `@spec` references, cross-reference them against specs, and display a coverage panel showing referenced vs unreferenced requirements. Scanning SHALL NOT be restricted to Java files, and SHALL NOT require explicitly configured source roots. Binary files SHALL be skipped.

#### Scenario: Coverage display
- **WHEN** the Coverage tab is selected
- **THEN** it SHALL show a tree of domains/requirements with referenced (green) and unreferenced (gray) indicators

#### Scenario: Coverage in a non-Java project
- **WHEN** a project contains no `.java` files but has source files in another language (for example `.kt`, `.go`, or `.py`) carrying `@spec` references that match existing requirements
- **THEN** those requirements SHALL be reported as referenced and the overall coverage SHALL be greater than 0%

#### Scenario: Binary files are ignored
- **WHEN** the project contains binary files (for example images or archives) under its content roots
- **THEN** the coverage scan SHALL skip them without error and SHALL NOT treat their bytes as `@spec` references