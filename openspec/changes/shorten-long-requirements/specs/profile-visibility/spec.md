## MODIFIED Requirements

### Requirement: Workflow profile resolution

The plugin SHALL resolve active workflows as the CLI does, from `openspec config list --json` and the detected CLI version, and cache them as a `Set<String>`. For the `custom` profile they SHALL be the stored `workflows` list, with `sync` added from CLI 1.10 when archiving needs it. For any other profile, or none, they SHALL be that CLI version's core set, ignoring any stored list. Without the CLI they SHALL be the current core set.

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

### Requirement: Profile-aware action enablement

Each action's workflow ID SHALL be checked against the active workflows at update time. An action whose workflow is not active SHALL be visible but disabled, with a descriptive tooltip. Gating SHALL apply only to preset-conditional actions. View and diff utility actions that return `null` from `getWorkflowId()` are out of scope.

#### Scenario: Workflow enabled in profile
- **WHEN** an action's workflow ID is present in the active workflows set
- **THEN** the action SHALL be enabled (subject to other existing enablement checks)

#### Scenario: Workflow not enabled in profile
- **WHEN** an action's workflow ID is not present in the active workflows set
- **THEN** the action SHALL be visible but disabled with a tooltip indicating how to enable it via Settings

#### Scenario: Utility action always enabled
- **WHEN** an action returns `null` for its workflow ID and the project is an OpenSpec project
- **THEN** the action SHALL be enabled regardless of the active profile

#### Scenario: View/diff utility never gated
- **WHEN** a view/diff utility action (e.g., Sync Specs) is evaluated for enablement
- **THEN** workflow gating SHALL NOT apply, even if the active workflow set excludes a string that coincides with the utility's name (e.g., `sync`)

#### Scenario: Non-OpenSpec project
- **WHEN** the project is not an OpenSpec project
- **THEN** all actions SHALL be hidden regardless of workflow ID or profile

### Requirement: User documentation for workflow profiles

The plugin SHALL ship user documentation of the project-profile versus workflow-profile split, when to use core or custom, what custom enables, the two-step profile change (`openspec config profile`, then `openspec update`), and how the profile-aware UI works. It SHALL frame core and custom as different in scope, not as an upgrade.

#### Scenario: Documentation page exists and is reachable from UI
- **WHEN** the documentation is published
- **THEN** there SHALL be a docs page on workflow profiles, AND the status bar widget popup AND the Settings panel context help SHALL link to it

#### Scenario: Rationale for core
- **WHEN** a reader chooses between core and custom
- **THEN** the documentation SHALL state the AI-context-preservation rationale for core
