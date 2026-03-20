# Actions

## Domain
Plugin actions for the OpenSpec workflow.

## Requirements

### Requirement: Init Action

The plugin SHALL provide an Init action that initializes OpenSpec in a project.

#### Scenario: Init creates config
- GIVEN a project without an openspec directory
- WHEN the user runs Init
- THEN the system SHALL create openspec/config.yaml

### Requirement: Propose Action

The plugin SHALL provide a Propose action that creates a new change.

#### Scenario: Propose creates change directory
- GIVEN an initialized OpenSpec project
- WHEN the user runs Propose with a name
- THEN the system SHALL create a change directory under openspec/changes/
