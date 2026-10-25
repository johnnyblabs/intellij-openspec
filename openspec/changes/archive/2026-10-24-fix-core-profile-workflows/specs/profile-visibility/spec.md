## MODIFIED Requirements

### Requirement: Workflow profile resolution

The plugin SHALL resolve the active workflows the way the OpenSpec CLI does (`getProfileWorkflows`), from `openspec config list --json` and the detected CLI version, and cache the result as a `Set<String>`. For the `custom` profile the active workflows SHALL be the stored `workflows` list, with `sync` added from CLI 1.10 when archiving needs it. For any other profile, or none, they SHALL be that CLI version's core set, and a stored list SHALL be ignored. Without the CLI the plugin SHALL use the current core set.

#### Scenario: CLI available
- **WHEN** the plugin resolves the active workflows and the CLI is available
- **THEN** it SHALL read `profile` and `workflows` from `openspec config list --json`, resolve them by the rules below, and cache the result

#### Scenario: CLI unavailable
- **WHEN** the plugin resolves the active workflows and the CLI is not available
- **THEN** it SHALL use the current core set: propose, explore, apply, update, sync, archive

#### Scenario: Cache refresh on profile change
- **WHEN** the user changes the workflow profile in Settings or the status bar widget and applies
- **THEN** the plugin SHALL refresh the cached workflows list

#### Scenario: Core ignores a stored list
- **WHEN** `config list --json` reports `profile: core` together with a `workflows` list, for example a leftover `["propose","explore","apply","archive"]`
- **THEN** the active workflows SHALL be the detected CLI version's core set, not the stored list

#### Scenario: No profile set
- **WHEN** `config list --json` has no `profile` or no `workflows`, as on a fresh config
- **THEN** the profile SHALL resolve to core and the active workflows to the detected CLI version's core set

#### Scenario: The core set follows the CLI version
- **WHEN** the resolved profile is core
- **THEN** the active workflows SHALL be `propose, explore, apply, archive` below CLI 1.4.0; `propose, explore, apply, sync, archive` from 1.4.0 below 1.6.0; and `propose, explore, apply, update, sync, archive` from 1.6.0

#### Scenario: Custom keeps its stored list
- **WHEN** `config list --json` reports `profile: custom` and a `workflows` list
- **THEN** the active workflows SHALL be exactly that list, apart from the `sync` rule below

#### Scenario: Custom archiving pulls in sync from CLI 1.10
- **WHEN** the profile is custom, the detected CLI is 1.10.0 or later, and the stored list contains `archive` or `bulk-archive` but not `sync`
- **THEN** the active workflows SHALL include `sync`, as the CLI installs it
- **AND** below CLI 1.10.0 the stored list SHALL be used unchanged
