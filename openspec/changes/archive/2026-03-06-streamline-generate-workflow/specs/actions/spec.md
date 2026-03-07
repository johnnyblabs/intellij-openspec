## REMOVED Requirements

### Requirement: Smart Delivery Method Default
**Reason**: Generation actions are removed from the menu bar. The delivery method resolution is now exclusively handled by the Workflow Action Panel, which already implements this behavior.
**Migration**: Use the Workflow Action Panel's Generate button, which uses the same DeliveryMethodResolver.

## MODIFIED Requirements

### Requirement: Action Availability

Actions SHALL only be enabled when the current project is an OpenSpec project. The menu bar SHALL NOT include Generate Artifact or Generate All Artifacts actions.

#### Scenario: Non-OpenSpec project
- **GIVEN** a project without an `openspec/` directory
- **WHEN** the user opens the OpenSpec menu
- **THEN** OpenSpec actions SHALL be disabled

#### Scenario: OpenSpec menu contents
- **WHEN** the user opens the OpenSpec menu
- **THEN** the menu SHALL contain: Init, Propose, Apply, Archive, Validate, List, Refresh
- **THEN** the menu SHALL NOT contain Generate Artifact or Generate All Artifacts
