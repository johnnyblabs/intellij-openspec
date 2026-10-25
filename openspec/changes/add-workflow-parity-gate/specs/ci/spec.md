## ADDED Requirements

### Requirement: Upstream workflow parity

The test suite SHALL check the plugin's workflow-gated actions against upstream's workflow set, captured from the real CLI as a fixture rather than written by hand. Every workflow upstream defines SHALL either have a registered action gated on it or appear in a single recorded list of workflows the plugin deliberately doesn't surface, each with its reason. The plugin's core workflow set for the captured CLI version SHALL equal upstream's.

#### Scenario: A new upstream workflow
- **WHEN** a re-captured upstream workflow set contains a workflow with no action gated on it and no entry in the not-surfaced list
- **THEN** the test suite SHALL fail and name that workflow

#### Scenario: An action gated on a workflow upstream doesn't have
- **WHEN** an action is gated on a workflow id that isn't in upstream's workflow set
- **THEN** the test suite SHALL fail and name the action and the id

#### Scenario: A stale not-surfaced entry
- **WHEN** a workflow in the not-surfaced list now has an action gated on it, or is no longer an upstream workflow
- **THEN** the test suite SHALL fail, so the list can't go stale

#### Scenario: Core set matches upstream
- **WHEN** the plugin resolves the core profile for the captured CLI version
- **THEN** the result SHALL equal upstream's core workflow set for that version
