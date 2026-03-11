## MODIFIED Requirements

### Requirement: Apply Action

The plugin SHALL provide an "Apply" action that assembles a full-context implementation prompt from the change's design, specs, and tasks, and delivers it via the user's selected tool/delivery method. The action SHALL be available from the menu bar, tool window toolbar, and tree context menu.

#### Scenario: Apply with clipboard delivery
- **WHEN** the user triggers Apply from the menu or toolbar
- **THEN** the system SHALL assemble a prompt containing the change name, design.md content, spec file contents, and tasks.md content
- **AND** deliver it via the selected tool's delivery method

#### Scenario: Apply with no active change
- **WHEN** the user triggers Apply with no active changes
- **THEN** the system SHALL show a warning: "No active changes to apply"

#### Scenario: Apply with incomplete artifacts
- **WHEN** the user triggers Apply for a change where not all required artifacts are complete
- **THEN** the system SHALL show a warning indicating which artifacts are still needed

#### Scenario: Apply with all tasks already complete
- **WHEN** the user triggers Apply for a change where all tasks are marked complete
- **THEN** the system SHALL show a message: "All tasks complete" with guidance to archive
