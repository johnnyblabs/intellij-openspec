## MODIFIED Requirements

### Requirement: Workflow Action Panel Display

The tool window SHALL display a Workflow Action Panel between the tree and the status bar that shows the selected change's name via a change selector, artifact pipeline status, and generation controls.

#### Scenario: Active change with ready artifacts
- **WHEN** an active change is selected with ready artifacts
- **THEN** the panel SHALL display the change selector, a pipeline status row showing each artifact's state, and a "Generate [artifact-name]" button for the next ready artifact

#### Scenario: No active change
- **WHEN** no active change exists
- **THEN** the panel SHALL display guidance text such as "No active change" with a hint to use Propose

#### Scenario: All artifacts complete
- **WHEN** all artifacts for the selected change are done
- **THEN** the panel SHALL display "All complete" in the pipeline with guidance to apply or archive

## ADDED Requirements

### Requirement: Artifact Pipeline Visualization

The WorkflowActionPanel SHALL display a compact pipeline status row showing the state of each artifact in the change's DAG.

#### Scenario: Pipeline with mixed states
- **WHEN** the selected change has artifacts in done, ready, and blocked states
- **THEN** the pipeline row SHALL show each artifact as a labeled chip with a visual state indicator (e.g., checkmark for done, filled circle for ready, empty circle for blocked)

#### Scenario: Pipeline updates after generation
- **WHEN** an artifact generation completes
- **THEN** the pipeline row SHALL refresh to show the updated states

### Requirement: Context Menu Generation Routing

When a user triggers generation from the tree context menu on a change node, the panel SHALL select that change and initiate generation.

#### Scenario: Right-click generate on change
- **WHEN** the user right-clicks a change node and selects "Generate..."
- **THEN** the panel SHALL set its active change to that change and trigger the Generate button action
