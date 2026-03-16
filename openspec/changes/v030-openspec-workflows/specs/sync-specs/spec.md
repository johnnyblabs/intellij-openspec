## ADDED Requirements

### Requirement: Delta spec synchronization

The plugin SHALL provide a Sync Specs action that merges delta specs from a change into main specs, handling ADDED, MODIFIED, REMOVED, and RENAMED requirement operations.

#### Scenario: Added requirements
- **WHEN** a delta spec contains `## ADDED Requirements`
- **THEN** the plugin SHALL append those requirements to the main spec, creating the spec file if it does not exist

#### Scenario: Modified requirements
- **WHEN** a delta spec contains `## MODIFIED Requirements`
- **THEN** the plugin SHALL locate the matching requirement in the main spec and replace it with the modified version

#### Scenario: Removed requirements
- **WHEN** a delta spec contains `## REMOVED Requirements`
- **THEN** the plugin SHALL remove the matching requirement block from the main spec

#### Scenario: Renamed requirements
- **WHEN** a delta spec contains `## RENAMED Requirements` with FROM:/TO: format
- **THEN** the plugin SHALL rename the requirement header in the main spec

### Requirement: Sync preview

The plugin SHALL display a preview dialog showing the diff between current main specs and the result of applying delta specs before committing changes.

#### Scenario: Preview diff
- **WHEN** the user triggers Sync Specs
- **THEN** the plugin SHALL show a side-by-side diff of each affected main spec before and after the merge

#### Scenario: Confirm and apply
- **WHEN** the user confirms the preview
- **THEN** the plugin SHALL apply the changes via WriteAction and refresh the tool window
