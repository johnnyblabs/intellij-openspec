## ADDED Requirements

### Requirement: Workflow profile resolution

The plugin SHALL resolve the active workflows list from the global OpenSpec config via CLI (`openspec config list --json`), caching the result as a `Set<String>` for efficient lookup. If the CLI is unavailable, the plugin SHALL fall back to the core default workflows: propose, explore, apply, archive.

#### Scenario: CLI available
- **WHEN** the plugin resolves the active workflows and the CLI is available
- **THEN** it SHALL parse the `workflows` array from `openspec config list --json` and cache the result

#### Scenario: CLI unavailable
- **WHEN** the plugin resolves the active workflows and the CLI is not available
- **THEN** it SHALL use the core default set: propose, explore, apply, archive

#### Scenario: Cache refresh on profile change
- **WHEN** the user changes the profile in Settings and applies
- **THEN** the plugin SHALL refresh the cached workflows list

### Requirement: Workflow ID mapping

Each workflow-bound action SHALL declare its workflow ID via a `getWorkflowId()` method. Utility actions that do not map to a workflow SHALL return `null`.

#### Scenario: Workflow action declares ID
- **WHEN** a workflow-bound action (e.g., Fast-Forward, Continue, Verify, Sync, Bulk Archive, Propose, Explore, Apply, Archive) is queried for its workflow ID
- **THEN** it SHALL return the corresponding workflow string (e.g., `"ff"`, `"continue"`, `"verify"`, `"sync"`, `"bulk-archive"`, `"propose"`, `"explore"`, `"apply"`, `"archive"`)

#### Scenario: Utility action has no workflow ID
- **WHEN** a utility action (Init, Validate, List, Refresh, Update, ManageTools, SetupWizard) is queried for its workflow ID
- **THEN** it SHALL return `null`

### Requirement: Profile-aware action enablement

The plugin SHALL check each action's workflow ID against the active workflows during action update. Actions whose workflow is not in the active profile SHALL be visible but disabled with a descriptive tooltip.

#### Scenario: Workflow enabled in profile
- **WHEN** an action's workflow ID is present in the active workflows set
- **THEN** the action SHALL be enabled (subject to other existing enablement checks)

#### Scenario: Workflow not enabled in profile
- **WHEN** an action's workflow ID is not present in the active workflows set
- **THEN** the action SHALL be visible but disabled with a tooltip indicating how to enable it via Settings

#### Scenario: Utility action always enabled
- **WHEN** an action returns `null` for its workflow ID and the project is an OpenSpec project
- **THEN** the action SHALL be enabled regardless of the active profile

#### Scenario: Non-OpenSpec project
- **WHEN** the project is not an OpenSpec project
- **THEN** all actions SHALL be hidden regardless of workflow ID or profile
