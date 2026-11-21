## MODIFIED Requirements

### Requirement: Tracker creation on change inception

When a change is created via `/opsx:propose` or `/opsx:new`, the skill SHALL create a Forgejo issue and a Plane work item using the `scripts/.env` credentials. The issue SHALL use the change name as title, the proposal summary as body, appropriate labels and the current catch-all milestone. The work item SHALL include the change name, description, labels, priority and the current cycle. Both tracker IDs SHALL be written to the change's `.openspec.yaml` under `tracking`.

#### Scenario: Propose creates trackers
- **WHEN** a user runs `/opsx:propose` and the change is created with a proposal
- **THEN** the skill SHALL create a Forgejo issue and Plane work item, cross-link them in the descriptions, and write their IDs to `.openspec.yaml`

#### Scenario: New creates trackers
- **WHEN** a user runs `/opsx:new` and the change directory is created
- **THEN** the skill SHALL create a Forgejo issue and Plane work item with the change name and write their IDs to `.openspec.yaml`

#### Scenario: Tokens missing — graceful skip
- **WHEN** `scripts/.env` does not exist or required tokens are empty
- **THEN** the skill SHALL log "Tracker integration skipped — credentials not found" and proceed without creating trackers

#### Scenario: API error — non-blocking
- **WHEN** the Forgejo or Plane API returns an error during creation
- **THEN** the skill SHALL log the error and continue without failing the change creation
