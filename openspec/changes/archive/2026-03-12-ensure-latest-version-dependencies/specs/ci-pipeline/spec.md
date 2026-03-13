## ADDED Requirements

### Requirement: CI validates dependency upgrade safety gates
The CI pipeline SHALL enforce dependency upgrade safety gates by running compile, test, and plugin verification checks before dependency update changes are considered valid.

#### Scenario: Dependency update change passes required CI gates
- **WHEN** a change includes dependency version updates
- **THEN** the CI pipeline SHALL run build, test, and plugin verification jobs for that change

#### Scenario: Dependency update change fails verification
- **WHEN** any dependency update introduces compile, test, or plugin verification failures
- **THEN** the CI pipeline SHALL fail the run and SHALL block merge until failures are resolved

### Requirement: CI supports phased dependency upgrade validation
The CI pipeline SHALL support phased dependency upgrade validation so failures can be isolated by dependency tier.

#### Scenario: Tooling phase validation runs first
- **WHEN** dependency updates include build or IntelliJ platform tooling changes
- **THEN** CI SHALL validate tooling changes before validating runtime dependency changes

#### Scenario: Runtime phase runs after tooling and test phases pass
- **WHEN** tooling and test dependency phases pass
- **THEN** CI SHALL validate runtime dependency upgrades as the final phase

