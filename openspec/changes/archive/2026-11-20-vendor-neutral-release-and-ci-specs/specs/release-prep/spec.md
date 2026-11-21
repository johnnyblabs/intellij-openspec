## MODIFIED Requirements

### Requirement: Tracker state validation

The `/release-prep` skill SHALL check that all issue-tracker items in the release milestone are closed and all work-item-tracker items in the release cycle are in the Done state.

#### Scenario: All issues closed
- **WHEN** every issue in the release milestone is closed
- **THEN** the skill SHALL report the issue tracker as clean

#### Scenario: Open issues remain
- **WHEN** open issues exist in the release milestone
- **THEN** the skill SHALL list them as warnings (not blockers — some issues may span releases)

#### Scenario: All work items done
- **WHEN** every work item in the release cycle is Done
- **THEN** the skill SHALL report the work-item tracker as clean

#### Scenario: Incomplete work items
- **WHEN** work items in the release cycle are not Done
- **THEN** the skill SHALL list them as warnings

### Requirement: Milestone and cycle auto-creation

The `/release-prep` skill SHALL create an issue-tracker milestone and a work-item cycle for the release version if they do not already exist. Relevant issues/work items SHALL be reassigned from the catch-all milestone/cycle to the new release-specific one.

#### Scenario: Milestone does not exist
- **WHEN** no issue-tracker milestone matches the release version
- **THEN** the skill SHALL create it and move relevant issues (those with matching archived changes) from the catch-all milestone

#### Scenario: Milestone already exists
- **WHEN** an issue-tracker milestone already matches the release version
- **THEN** the skill SHALL use the existing milestone

#### Scenario: Cycle does not exist
- **WHEN** no work-item cycle matches the release version
- **THEN** the skill SHALL create it and move relevant work items from the catch-all cycle
