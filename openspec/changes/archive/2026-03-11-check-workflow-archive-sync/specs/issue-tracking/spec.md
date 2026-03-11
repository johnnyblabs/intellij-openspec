## ADDED Requirements

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

