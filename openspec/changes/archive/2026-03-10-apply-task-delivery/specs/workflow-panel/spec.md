## MODIFIED Requirements

### Requirement: Workflow Action Panel Display

The tool window SHALL display a Workflow Action Panel between the tree and the status bar that shows the selected change's name via a change selector, artifact pipeline status, tool/delivery selector, and generation controls.

#### Scenario: Active change with ready artifacts
- **WHEN** an active change is selected with ready artifacts
- **THEN** the panel SHALL display the change selector, a pipeline status row, a tool/delivery selector, and a "Generate [artifact-name]" button for the next ready artifact

#### Scenario: No active change
- **WHEN** no active change exists
- **THEN** the panel SHALL display guidance text such as "No active change" with a hint to use Propose

#### Scenario: All artifacts complete with remaining tasks
- **WHEN** all artifacts for the selected change are done and tasks.md contains incomplete tasks
- **THEN** the panel SHALL display an "Apply Tasks" button with the tool selector, a task progress indicator (e.g., "3/12 tasks complete"), and the pipeline chips showing all-done state

#### Scenario: All artifacts and tasks complete
- **WHEN** all artifacts for the selected change are done and all tasks are marked complete
- **THEN** the panel SHALL display "All complete" in the pipeline with guidance to archive

#### Scenario: Large task list hint
- **WHEN** all artifacts are complete and 10 or more tasks remain incomplete
- **THEN** the panel SHALL display an inline hint suggesting the user review tasks.md before applying

#### Scenario: Generate All button visibility
- **WHEN** Direct API is configured and 2 or more artifacts remain to be generated
- **THEN** the panel SHALL display a "Generate All" button alongside the existing Generate button

#### Scenario: Generate All button hidden without API
- **WHEN** Direct API is not configured
- **THEN** the panel SHALL NOT display the "Generate All" button

#### Scenario: Generate All button hidden with single artifact
- **WHEN** only 1 artifact remains to be generated
- **THEN** the panel SHALL NOT display the "Generate All" button

## ADDED Requirements

### Requirement: Post-Apply watching state
The WorkflowActionPanel SHALL display a watching state after an Apply delivery, monitoring tasks.md for progress.

#### Scenario: Watching state after clipboard Apply
- **WHEN** the Apply prompt is copied to the clipboard
- **THEN** the panel SHALL show "Watching for task progress..." with tool-specific instructions and a "Check progress" button
- **AND** the Apply button SHALL display "Waiting..." in a disabled state

#### Scenario: Task progress updates during watch
- **WHEN** tasks.md is modified during the watching state
- **THEN** the task progress indicator SHALL update to reflect the new completion count

#### Scenario: Watch completes when all tasks done
- **WHEN** all tasks are marked complete during the watching state
- **THEN** the watching state SHALL be dismissed and the panel SHALL show "All complete" with archive guidance
