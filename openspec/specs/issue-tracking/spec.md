# Issue Tracking

## Purpose
Automated issue lifecycle integration with Forgejo and Plane project management systems. This is internal development tooling — tracker configuration is stored in project settings and `scripts/.env`, not exposed in the user-facing settings UI.

## Requirements

### Requirement: Forgejo issue management
The plugin SHALL provide a ForgejoService that creates, updates, and closes issues in a configured Forgejo instance via its REST API.

#### Scenario: Create issue on propose
- **WHEN** a change is successfully proposed and Forgejo tracking is enabled
- **THEN** the system SHALL create a Forgejo issue with the change name as title and proposal.md content as body
- **AND** store the issue number and URL in the change's `.openspec.yaml` under `tracking.forgejo`

#### Scenario: Update issue on apply
- **WHEN** the Apply action is triggered for a change with a linked Forgejo issue
- **THEN** the system SHALL add a comment to the issue indicating implementation has started
- **AND** add the `in-progress` label to the issue

#### Scenario: Close issue on archive
- **WHEN** the Archive action completes for a change with a linked Forgejo issue
- **THEN** the system SHALL close the issue and add a comment indicating the change was archived
- **AND** add the `done` label to the issue

#### Scenario: Forgejo unavailable
- **WHEN** the Forgejo API call fails due to network error or authentication failure
- **THEN** the system SHALL show a warning notification but SHALL NOT block the primary action
- **AND** the failure SHALL be logged at WARN level

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

### Requirement: Issue lifecycle orchestration
The plugin SHALL provide an IssueLifecycleService that coordinates Forgejo and Plane tracking operations during change lifecycle events.

#### Scenario: Both trackers enabled
- **WHEN** a lifecycle event fires and both Forgejo and Plane are enabled
- **THEN** the system SHALL call both trackers and report results independently

#### Scenario: Only one tracker enabled
- **WHEN** a lifecycle event fires and only one tracker is enabled
- **THEN** the system SHALL call only the enabled tracker

#### Scenario: No trackers enabled
- **WHEN** a lifecycle event fires and no trackers are enabled
- **THEN** the system SHALL skip tracking silently with no notifications

#### Scenario: Background execution
- **WHEN** a lifecycle event fires with trackers enabled
- **THEN** tracking operations SHALL execute on a background thread and SHALL NOT block the EDT

### Requirement: Tracker credential storage
The plugin SHALL store Forgejo tokens and Plane API keys securely using IntelliJ's PasswordSafe.

#### Scenario: Store Forgejo token
- **WHEN** the user enters a Forgejo token in settings and applies
- **THEN** the token SHALL be stored via PasswordSafe under a `OpenSpec-Tracker-` service prefix

#### Scenario: Store Plane API key
- **WHEN** the user enters a Plane API key in settings and applies
- **THEN** the key SHALL be stored via PasswordSafe under a `OpenSpec-Tracker-` service prefix

#### Scenario: Token removal
- **WHEN** the user clears a stored token in settings
- **THEN** the token SHALL be removed from PasswordSafe

### Requirement: Tracking metadata in .openspec.yaml
The plugin SHALL store tracker references in the change's `.openspec.yaml` file under an optional `tracking` block.

#### Scenario: Metadata written after issue creation
- **WHEN** a Forgejo issue or Plane work item is successfully created
- **THEN** the system SHALL write the issue/work-item ID and URL to `.openspec.yaml` under the `tracking` block

#### Scenario: Metadata survives archive
- **WHEN** a change is archived
- **THEN** the `tracking` block in `.openspec.yaml` SHALL be preserved in the archived copy

#### Scenario: Missing metadata on apply/archive
- **WHEN** Apply or Archive is triggered for a change with no `tracking` block
- **THEN** the system SHALL skip tracker updates for that change silently

### Requirement: Tracker connection testing
The tracker services SHALL provide `testConnection()` methods that validate the connection programmatically. Connection testing is available via setup scripts (`scripts/setup-forgejo.sh`, `scripts/setup-plane.sh`), not through the plugin settings UI.

#### Scenario: Successful Forgejo test
- **WHEN** `ForgejoService.testConnection()` is called with valid credentials
- **THEN** it SHALL return a success message confirming the connection

#### Scenario: Failed Forgejo test
- **WHEN** `ForgejoService.testConnection()` is called with invalid credentials
- **THEN** it SHALL throw an exception with the failure reason

#### Scenario: Successful Plane test
- **WHEN** `PlaneService.testConnection()` is called with valid credentials
- **THEN** it SHALL return a success message confirming the connection

#### Scenario: Failed Plane test
- **WHEN** `PlaneService.testConnection()` is called with invalid credentials
- **THEN** it SHALL throw an exception with the failure reason

### Requirement: Post-archive tracker sync is idempotent
The issue lifecycle integration SHALL treat post-archive sync updates as idempotent so repeated sync attempts do not create duplicate terminal updates.

#### Scenario: Repeated sync does not duplicate Forgejo closure updates
- **WHEN** sync reconciliation runs multiple times for an archived change linked to Forgejo
- **THEN** the system SHALL avoid duplicate close, comment, or label updates that are already applied

#### Scenario: Repeated sync does not duplicate Plane terminal updates
- **WHEN** sync reconciliation runs multiple times for an archived change linked to Plane
- **THEN** the system SHALL avoid duplicate terminal state transitions that are already applied

### Requirement: Tracker sync failures do not invalidate archive
The issue lifecycle integration SHALL preserve archive completion even when tracker sync updates fail.

#### Scenario: Tracker sync network failure after archive
- **WHEN** archive succeeds and tracker sync fails due to network or authentication error
- **THEN** the system SHALL keep the change archived and SHALL mark tracker sync as a recoverable failure

#### Scenario: Missing tracking metadata during sync
- **WHEN** sync reconciliation runs for an archived change with missing tracking metadata
- **THEN** the system SHALL skip tracker updates for missing trackers and SHALL complete remaining tracker updates
