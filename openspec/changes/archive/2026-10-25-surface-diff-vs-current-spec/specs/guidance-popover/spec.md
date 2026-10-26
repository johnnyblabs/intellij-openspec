## ADDED Requirements

### Requirement: Diff link after generating specs

When Direct API generates or regenerates the specs artifact, the confirmation popover SHALL include a "Diff vs current spec" link.

#### Scenario: One capability
- **WHEN** the generated specs cover one capability and the user clicks the link
- **THEN** the plugin SHALL open that capability's diff against the current main spec

#### Scenario: Several capabilities
- **WHEN** the generated specs cover several capabilities and the user clicks the link
- **THEN** the plugin SHALL ask which capability, then open its diff

#### Scenario: Other artifacts
- **WHEN** Direct API generates an artifact other than specs
- **THEN** the popover SHALL NOT include the link
