## ADDED Requirements

### Requirement: Active workflows follow a CLI version change

When CLI detection reports a version different from the one the active workflows were resolved for, including the CLI appearing or disappearing, the plugin SHALL re-resolve the active workflows off the EDT, so action gating follows the new CLI without reopening the project. A detection that reports the same version SHALL NOT re-resolve them.

#### Scenario: CLI upgraded during a session
- **WHEN** the active workflows were resolved for CLI 1.5.0 on the core profile and detection then reports CLI 1.6.0
- **THEN** the active workflows SHALL be re-resolved to the 1.6.0 core set, which includes `update`, and actions gated on `update` SHALL become enabled

#### Scenario: Same version detected again
- **WHEN** detection reports the version the active workflows were already resolved for
- **THEN** the active workflows SHALL NOT be re-resolved

#### Scenario: CLI removed during a session
- **WHEN** detection reports that the CLI is no longer available
- **THEN** the active workflows SHALL be re-resolved to the current core set, as when the CLI is unavailable at startup
