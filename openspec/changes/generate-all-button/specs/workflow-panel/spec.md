## MODIFIED Requirements

### Requirement: Workflow Action Panel Display

The tool window SHALL display a Workflow Action Panel between the tree and the status bar that shows the selected change's name via a change selector, artifact pipeline status, and generation controls.

#### Scenario: Active change with ready artifacts
- WHEN an active change is selected with ready artifacts
- THEN the panel SHALL display the change selector, a pipeline status row showing each artifact's state, and a "Generate [artifact-name]" button for the next ready artifact

#### Scenario: No active change
- WHEN no active change exists
- THEN the panel SHALL display guidance text such as "No active change" with a hint to use Propose

#### Scenario: All artifacts complete
- WHEN all artifacts for the selected change are done
- THEN the panel SHALL display "All complete" in the pipeline with guidance to apply or archive

#### Scenario: Generate All button visibility
- WHEN Direct API is configured and 2 or more artifacts remain to be generated
- THEN the panel SHALL display a "Generate All" button alongside the existing Generate button

#### Scenario: Generate All button hidden without API
- WHEN Direct API is not configured
- THEN the panel SHALL NOT display the "Generate All" button

#### Scenario: Generate All button hidden with single artifact
- WHEN only 1 artifact remains to be generated
- THEN the panel SHALL NOT display the "Generate All" button

## ADDED Requirements

### Requirement: Generate All UI progress feedback

The WorkflowActionPanel SHALL display real-time progress during a Generate All operation.

#### Scenario: Progress label during generation
- WHEN a Generate All operation is in progress
- THEN the panel SHALL display a progress label showing the current artifact and count (e.g., "Generating design... 2/4")

#### Scenario: Pipeline chips update in real-time
- WHEN an artifact completes during a Generate All chain
- THEN the pipeline chip for that artifact SHALL update to show the done state before the next artifact begins

#### Scenario: Buttons disabled during generation
- WHEN a Generate All operation is in progress
- THEN both the Generate and Generate All buttons SHALL be disabled and a Cancel button SHALL be shown

#### Scenario: Cancel button stops generation
- WHEN the user clicks Cancel during a Generate All operation
- THEN the system SHALL cancel the remaining chain and restore the panel to its normal state showing current progress

#### Scenario: Completion restores normal state
- WHEN a Generate All operation completes successfully
- THEN the panel SHALL restore to normal state with all pipeline chips showing done and an "All complete" message

#### Scenario: Error shows notification and restores state
- WHEN a Generate All operation fails on an artifact
- THEN the panel SHALL show an error notification identifying the failed artifact, and restore to normal state with completed artifacts shown as done
