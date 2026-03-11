## MODIFIED Requirements

### Requirement: Plane work item management
The plugin SHALL provide a PlaneService that creates and updates work items in a configured Plane instance via its REST API. The service SHALL use configured workflow state names for state transitions.

#### Scenario: Create work item on propose
- **WHEN** a change is successfully proposed and Plane tracking is enabled
- **THEN** the system SHALL create a Plane work item with the change name as title and proposal.md content converted to HTML as description
- **AND** store the work item ID and URL in the change's `.openspec.yaml` under `tracking.plane`
- **AND** set the work item state to "Backlog"

#### Scenario: Update work item on apply
- **WHEN** the Apply action is triggered for a change with a linked Plane work item
- **THEN** the system SHALL update the work item state to "In Progress"

#### Scenario: Close work item on archive
- **WHEN** the Archive action completes for a change with a linked Plane work item
- **THEN** the system SHALL update the work item state to "Done"

#### Scenario: Plane unavailable
- **WHEN** the Plane API call fails due to network error or authentication failure
- **THEN** the system SHALL show a warning notification but SHALL NOT block the primary action
