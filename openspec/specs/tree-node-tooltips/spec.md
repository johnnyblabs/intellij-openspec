# Tree Node Tooltips

## Purpose
Contextual hover tooltips on all spec tree nodes providing information not visible in the node label.

### Requirement: Tree node tooltips
Every node in the spec tree SHALL display a contextual tooltip on hover providing information not visible in the node label.

#### Scenario: Spec domain tooltip shows requirement count and path
- **WHEN** the user hovers over a spec domain node
- **THEN** the tooltip SHALL show the number of requirements and the file path

#### Scenario: Change node tooltip shows path
- **WHEN** the user hovers over a change node
- **THEN** the tooltip SHALL show the change directory path

#### Scenario: Artifact done tooltip shows completion and path
- **WHEN** the user hovers over a completed artifact node
- **THEN** the tooltip SHALL indicate the artifact is complete and show the file path

#### Scenario: Artifact ready tooltip shows generation prompt
- **WHEN** the user hovers over a ready artifact node
- **THEN** the tooltip SHALL indicate the artifact is ready to generate

#### Scenario: Artifact blocked tooltip shows blocking dependencies
- **WHEN** the user hovers over a blocked artifact node
- **THEN** the tooltip SHALL list the dependencies that must complete first

#### Scenario: Missing artifact tooltip shows status
- **WHEN** the user hovers over a missing artifact node
- **THEN** the tooltip SHALL indicate the artifact has not yet been created

#### Scenario: Delta spec tooltip shows path
- **WHEN** the user hovers over a delta spec node
- **THEN** the tooltip SHALL show the delta spec file path

#### Scenario: Hint node tooltip repeats guidance
- **WHEN** the user hovers over a hint node
- **THEN** the tooltip SHALL display the hint text

#### Scenario: Section nodes show descriptive tooltip
- **WHEN** the user hovers over the Specs, Changes, or Archive section node
- **THEN** the tooltip SHALL show a brief description of the section's purpose
