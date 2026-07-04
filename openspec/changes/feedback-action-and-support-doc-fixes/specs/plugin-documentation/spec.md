# Delta — plugin-documentation

## MODIFIED Requirements

### Requirement: OpenSpec client coverage matrix is published

The project SHALL publish and maintain a coverage matrix at `docs/openspec-support.md` that maps OpenSpec client capabilities to the plugin's support status, with each capability annotated by the minimum CLI version it requires. The matrix SHALL distinguish supported, partial, divergent, planned, and plugin-original capabilities, and SHALL state the plugin's CLI-version support contract (minimum supported version, baseline, and runtime version-awareness). Support-mechanism classifications (delegated to the CLI, built-in, or surfaced indirectly) SHALL reflect the verified behavior of the code they describe; a claim that a capability is delegated when the implementation is built-in (or vice versa) is a documentation defect.

#### Scenario: Matrix reflects client coverage with version annotations
- **WHEN** a user reads `docs/openspec-support.md`
- **THEN** it SHALL present OpenSpec client capabilities grouped by area, each with a support status and a CLI-version annotation
- **AND** it SHALL state the minimum supported CLI version and that behavior degrades gracefully below it

#### Scenario: Mechanism classification matches code behavior
- **WHEN** the matrix classifies a capability's support mechanism (delegated / built-in / indirect)
- **THEN** that classification SHALL match how the plugin actually implements the capability, verified against the code path rather than assumed

#### Scenario: README links to the coverage matrix
- **WHEN** a user reads the README
- **THEN** the README SHALL link to the OpenSpec client coverage matrix

#### Scenario: Coverage matrix stays vendor-neutral on the public mirror
- **WHEN** the coverage matrix or its accompanying change artifacts are published to the public mirror
- **THEN** they SHALL reference only public identifiers (OpenSpec change names, CHANGELOG versions)
- **AND** they SHALL NOT contain internal tracker identifiers or environment-specific references
