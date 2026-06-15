## MODIFIED Requirements

### Requirement: Workflow ID mapping

Each workflow-bound action SHALL declare its workflow ID via a `getWorkflowId()` method. Utility actions that do not map to a workflow — including view/diff utilities like Sync Specs, Validate, and List that are not appropriate to gate on workflow membership — SHALL return `null`.

#### Scenario: Workflow action declares ID
- **WHEN** a workflow-bound action (e.g., Fast-Forward, Continue, Verify, Bulk Archive, Propose, Explore, Apply, Archive) is queried for its workflow ID
- **THEN** it SHALL return the corresponding workflow string (e.g., `"ff"`, `"continue"`, `"verify"`, `"bulk-archive"`, `"propose"`, `"explore"`, `"apply"`, `"archive"`)

#### Scenario: Utility action has no workflow ID
- **WHEN** a utility action (Init, Validate, List, Refresh, Update, ManageTools, SetupWizard, Sync Specs) is queried for its workflow ID
- **THEN** it SHALL return `null`

### Requirement: Profile-aware action enablement

The plugin SHALL check each action's workflow ID against the active workflows during action update. Actions whose workflow is not in the active profile SHALL be visible but disabled with a descriptive tooltip. Workflow gating SHALL apply only to actions whose CLI command is preset-conditional; view/diff utility actions (those that return `null` from `getWorkflowId()`) are out of scope for workflow-profile gating regardless of whether their conceptual workflow ID happens to match a CLI workflow string.

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