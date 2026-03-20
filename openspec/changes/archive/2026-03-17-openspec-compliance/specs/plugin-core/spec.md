## MODIFIED Requirements

### Requirement: Notification system

The plugin SHALL deliver categorized notifications with titles, actions, and bulk operation summaries. API errors SHALL include human-readable messages with actionable suggestions.

#### Scenario: Notifications
- **WHEN** the plugin reports status to the user
- **THEN** notifications SHALL use registered groups, include contextual titles, and provide action links where appropriate

## ADDED Requirements

### Requirement: ComplianceService registration
The plugin SHALL register `ComplianceService` as a project-level service (`@Service(Service.Level.PROJECT)`) in `plugin.xml`. The service SHALL be injectable via `project.getService(ComplianceService.class)` and SHALL compose `BuiltInValidator`, `VerificationService`, and `SpecSyncService` to compute compliance results.

#### Scenario: Service registration
- **WHEN** the plugin starts for a project
- **THEN** `ComplianceService` SHALL be available via `project.getService(ComplianceService.class)`

#### Scenario: Service composes existing services
- **WHEN** `ComplianceService.checkCompliance(change)` is called
- **THEN** it SHALL delegate to `BuiltInValidator` for validation, `VerificationService` for artifact completeness, and `SpecSyncService` for delta spec readiness
