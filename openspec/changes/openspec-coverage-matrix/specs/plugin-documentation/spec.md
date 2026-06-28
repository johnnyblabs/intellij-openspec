# plugin-documentation (delta)

## ADDED Requirements

### Requirement: OpenSpec client coverage matrix is published

The project SHALL publish and maintain a coverage matrix at `docs/openspec-support.md` that maps OpenSpec client capabilities to the plugin's support status, with each capability annotated by the minimum CLI version it requires. The matrix SHALL distinguish supported, partial, divergent, planned, and plugin-original capabilities, and SHALL state the plugin's CLI-version support contract (minimum supported version, baseline, and runtime version-awareness).

#### Scenario: Matrix reflects client coverage with version annotations
- **WHEN** a user reads `docs/openspec-support.md`
- **THEN** it SHALL present OpenSpec client capabilities grouped by area, each with a support status and a CLI-version annotation
- **AND** it SHALL state the minimum supported CLI version and that behavior degrades gracefully below it

#### Scenario: README links to the coverage matrix
- **WHEN** a user reads the README
- **THEN** the README SHALL link to the OpenSpec client coverage matrix

#### Scenario: Coverage matrix stays vendor-neutral on the public mirror
- **WHEN** the coverage matrix or its accompanying change artifacts are published to the public mirror
- **THEN** they SHALL reference only public identifiers (OpenSpec change names, CHANGELOG versions)
- **AND** they SHALL NOT contain internal tracker identifiers or environment-specific references
