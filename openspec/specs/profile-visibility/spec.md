# Profile Visibility

## Purpose
Profile-aware action enablement — disabling actions whose workflow is not in the active workflow profile (`core` or `custom`), with tooltip guidance.

## Requirements

### Requirement: Workflow profile resolution

The plugin SHALL resolve the active workflows list from the global OpenSpec config via CLI (`openspec config list --json`), caching the result as a `Set<String>` for efficient lookup. If the CLI is unavailable, the plugin SHALL fall back to the OpenSpec 1.2.0+ core default workflows: propose, explore, apply, sync, archive.

#### Scenario: CLI available
- **WHEN** the plugin resolves the active workflows and the CLI is available
- **THEN** it SHALL parse the `workflows` array from `openspec config list --json` and cache the result

#### Scenario: CLI unavailable
- **WHEN** the plugin resolves the active workflows and the CLI is not available
- **THEN** it SHALL use the core default set: propose, explore, apply, sync, archive

#### Scenario: Cache refresh on profile change
- **WHEN** the user changes the workflow profile in Settings or the status bar widget and applies
- **THEN** the plugin SHALL refresh the cached workflows list

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

### Requirement: Active workflow profile status bar widget

The plugin SHALL provide a status bar widget that displays the name of the currently active OpenSpec workflow profile. The widget SHALL be available only when the project is an OpenSpec project (using the same check applied to action enablement). Clicking the widget SHALL open a list popup of the available presets for one-click switching.

#### Scenario: Widget displays active profile in OpenSpec project
- **WHEN** the user opens an OpenSpec project
- **THEN** the IDE status bar SHALL display a widget showing the active workflow profile name (e.g., "OpenSpec: core" for core, "OpenSpec: custom · 8 workflows" for custom)

#### Scenario: Widget hidden in non-OpenSpec project
- **WHEN** the user opens a project that is not an OpenSpec project
- **THEN** the status bar widget SHALL NOT be displayed

#### Scenario: Widget click opens profile picker
- **WHEN** the user clicks the status bar widget
- **THEN** a list popup SHALL appear listing the active profile (selected), the other available preset, a non-selectable static discovery cue pointing at the CLI's `openspec config profile` interactive picker, an "Edit in Settings" link, and an "About profiles" docs link

#### Scenario: CLI unavailable
- **WHEN** the OpenSpec CLI is not available
- **THEN** the widget SHALL display the fallback profile name with a muted/fallback indicator and a tooltip explaining the CLI is not detected

#### Scenario: Switching profile from widget triggers post-switch update prompt
- **WHEN** the user switches profile via the status bar widget popup
- **THEN** the plugin SHALL invoke the same post-switch flow used by the Settings panel, including the prompt to run `openspec update`

### Requirement: Action text suffix for profile-disabled actions

When `OpenSpecBaseAction.update()` disables an action because its workflow is not in the active profile, the action's display text SHALL be suffixed with "(custom)" so the user understands the gating reason without hovering for a tooltip.

#### Scenario: Disabled-by-profile action suffixed
- **WHEN** an action is disabled because its workflow is not in the active profile
- **THEN** the action's display text SHALL include the suffix " (custom)"

#### Scenario: Enabled action not suffixed
- **WHEN** an action's workflow IS in the active profile
- **THEN** the action's display text SHALL NOT include any profile suffix

#### Scenario: Action disabled for non-profile reasons
- **WHEN** an action is disabled by a check unrelated to profile (e.g., non-OpenSpec project, missing prerequisite)
- **THEN** the action's display text SHALL NOT include the "(custom)" suffix

### Requirement: Active profile name exposed by service

`WorkflowProfileService` SHALL expose the name of the active workflow profile in addition to the active workflows set, so UI surfaces (status bar widget, settings, future tool window header) can display the profile name without re-querying the CLI.

#### Scenario: Service returns active profile name
- **WHEN** any consumer calls `getActiveProfileName()` on `WorkflowProfileService`
- **THEN** the service SHALL return the name of the currently active workflow profile (or the fallback name when CLI is unavailable)

### Requirement: Active workflows publicly readable from service

`WorkflowProfileService` SHALL expose the active workflows set publicly so UI surfaces (status bar widget label and tooltip, Settings panel Config Profile section) can read the cached set without re-querying the CLI.

#### Scenario: Service exposes active workflows
- **WHEN** any consumer calls `getActiveWorkflows()` on `WorkflowProfileService`
- **THEN** the service SHALL return the cached set of active workflow IDs

### Requirement: Diff detection on workflow refresh

`WorkflowProfileService.refresh()` SHALL detect whether the active workflows set has changed since the previous refresh, enabling future consumers to react to external changes. v1 does not consume the diff signal; the requirement establishes the API surface so a follow-up change can add notifications without re-architecting the service.

#### Scenario: Refresh detects unchanged workflows
- **WHEN** `refresh()` runs and the resolved workflows set equals the previously cached set
- **THEN** the service SHALL record no change and consumers querying the diff signal SHALL receive false

#### Scenario: Refresh detects changed workflows
- **WHEN** `refresh()` runs and the resolved workflows set differs from the previously cached set
- **THEN** the service SHALL record the change and consumers querying the diff signal SHALL receive true

### Requirement: User documentation for workflow profiles

The plugin SHALL ship user-facing documentation covering: the project-profile-vs-workflow-profile semantic split in OpenSpec config; when to use core vs custom (with the AI-context-preservation rationale); what custom workflows enable; the two-step profile change process (`openspec config profile` then `openspec update`); and how the plugin's profile-aware UI surfaces (status bar widget, action text suffix, tooltip) work together. Copy SHALL frame core and custom as scope-different rather than upgrade-worthy.

#### Scenario: Documentation page exists and is reachable from UI
- **WHEN** the documentation is published
- **THEN** there SHALL be a docs page on workflow profiles, AND the status bar widget popup AND the Settings panel context help SHALL link to it
