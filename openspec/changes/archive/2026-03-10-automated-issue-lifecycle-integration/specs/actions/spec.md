## MODIFIED Requirements

### Requirement: Propose Action

The plugin SHALL provide a "Propose" action that prompts the user for a change name and optionally a motivation (why) and scope (what changes), then creates a proposal using the OpenSpec 1.2.0 compliant template. After proposing, the Workflow Action Panel SHALL update to show the new change and its first ready artifact. After successful proposal creation, the action SHALL trigger issue/work-item creation in configured trackers.

#### Scenario: Propose with all fields
- **WHEN** the user selects OpenSpec > Propose from the main menu bar
- **THEN** a dialog SHALL appear with a required "Change name" field and optional "Why" and "What Changes" multi-line text areas
- **WHEN** the user fills in all fields and confirms
- **THEN** the plugin SHALL create a proposal.md with the "Why" input in the `## Why` section and the "What Changes" input in the `## What Changes` section

#### Scenario: Propose updates workflow panel
- **WHEN** a change is successfully proposed
- **THEN** the Workflow Action Panel SHALL update to show the new change and its Generate button for the first ready artifact

#### Scenario: Propose with only name
- **WHEN** the user provides only a change name and leaves "Why" and "What Changes" blank
- **THEN** the plugin SHALL create a proposal.md with HTML comment placeholders in the `## Why` and `## What Changes` sections

#### Scenario: Propose dialog field labels
- **WHEN** the Propose New Change dialog is displayed
- **THEN** the text input fields SHALL be labeled "Change name:", "Why:", and "What Changes:"
- **THEN** only "Change name" SHALL be required for validation

#### Scenario: Propose triggers issue creation
- **WHEN** a change is successfully proposed and issue tracking is configured
- **THEN** the system SHALL trigger IssueLifecycleService.onPropose() on a background thread
- **AND** show a notification on success or warning on failure

### Requirement: Apply Action

The plugin SHALL provide an "Apply" action that assembles a full-context implementation prompt from the change's design, specs, and tasks, and delivers it via the user's selected tool/delivery method. The action SHALL be available from the menu bar, tool window toolbar, and tree context menu. After delivery, the action SHALL trigger issue status updates in configured trackers.

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

#### Scenario: Apply triggers issue update
- **WHEN** Apply is successfully delivered for a change with linked tracker issues
- **THEN** the system SHALL trigger IssueLifecycleService.onApply() on a background thread
