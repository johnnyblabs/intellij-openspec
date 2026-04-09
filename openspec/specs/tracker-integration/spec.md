## Purpose

The tracker integration capability automatically creates, links, and closes Forgejo issues and Plane work items in sync with the OpenSpec change lifecycle (creation, archival, and release).

## Requirements

### Requirement: Tracker creation on change inception

When a change is created via `/opsx:propose` or `/opsx:new`, the skill SHALL create a Forgejo issue and a Plane work item using the `scripts/.env` credentials. The Forgejo issue SHALL include the change name as the title, a summary from the proposal as the body, appropriate labels, and assignment to the current catch-all milestone. The Plane work item SHALL include the change name, description, labels, priority, and assignment to the current cycle. Both tracker IDs SHALL be written to the change's `.openspec.yaml` under a `tracking` section.

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

### Requirement: Tracker closure on archive

When a change is archived via `/opsx:archive`, the skill SHALL read the `tracking` section from `.openspec.yaml` and close the Forgejo issue (state: closed) and move the Plane work item to the Done state.

#### Scenario: Archive closes trackers
- **WHEN** a user archives a change that has `tracking.forgejo_issue` and `tracking.plane_work_item` in `.openspec.yaml`
- **THEN** the skill SHALL close the Forgejo issue with a comment referencing the archive, and update the Plane work item state to Done

#### Scenario: No tracking metadata
- **WHEN** a change's `.openspec.yaml` has no `tracking` section
- **THEN** the skill SHALL skip tracker closure and proceed with the archive normally

#### Scenario: Already closed — idempotent
- **WHEN** the Forgejo issue is already closed or the Plane work item is already Done
- **THEN** the API calls SHALL succeed without error (idempotent state transitions)

### Requirement: Tracking metadata in .openspec.yaml

The `.openspec.yaml` file for a change SHALL support an optional `tracking` section containing `forgejo_issue` (integer issue number) and `plane_work_item` (UUID string).

#### Scenario: Tracking section written
- **WHEN** trackers are created successfully
- **THEN** `.openspec.yaml` SHALL contain a `tracking` section with both IDs

#### Scenario: Tracking section absent
- **WHEN** trackers were not created (tokens missing or API error)
- **THEN** `.openspec.yaml` SHALL NOT have a `tracking` section

### Requirement: Label and priority inference

The skill SHALL infer Forgejo labels and Plane priority from the change name and proposal content. Bug-related keywords (fix, bug, deadlock, crash) SHALL map to the `bug` label and `urgent`/`high` priority. Feature keywords (add, new, support) SHALL map to `enhancement`. Infrastructure keywords (ci, pipeline, build, release) SHALL map to `infrastructure`.

#### Scenario: Bug-related change
- **WHEN** the change name or proposal contains bug-related keywords
- **THEN** the Forgejo issue SHALL be labeled `bug` and the Plane work item SHALL have `high` or `urgent` priority

#### Scenario: Enhancement change
- **WHEN** the change name or proposal contains feature keywords
- **THEN** the Forgejo issue SHALL be labeled `enhancement` and the Plane work item SHALL have `medium` priority

#### Scenario: No clear category
- **WHEN** no keywords match
- **THEN** the Forgejo issue SHALL be labeled `enhancement` (default) and the Plane work item SHALL have `medium` priority (default)
