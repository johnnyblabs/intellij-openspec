## ADDED Requirements

### Requirement: Archive action chains sync reconciliation
The plugin SHALL execute archive as the primary phase and SHALL execute sync reconciliation as a follow-up phase after a successful archive operation.

#### Scenario: Archive triggers sync on success
- **WHEN** the user runs Archive for a change and the archive filesystem operation succeeds
- **THEN** the system SHALL start sync reconciliation for that archived change

#### Scenario: Sync is not started when archive fails
- **WHEN** the user runs Archive for a change and the archive filesystem operation fails
- **THEN** the system SHALL NOT start sync reconciliation for that change

### Requirement: Archive action reports partial success clearly
The plugin SHALL report archive and sync as separate outcomes so users can recover from sync failures without losing archive results.

#### Scenario: Archive success and sync failure
- **WHEN** archive succeeds and sync reconciliation fails
- **THEN** the system SHALL report archive success with sync failure and SHALL provide guidance to retry sync

#### Scenario: Retry sync after partial success
- **WHEN** the user initiates sync retry for a previously archived change
- **THEN** the system SHALL execute sync reconciliation again without re-running archive

