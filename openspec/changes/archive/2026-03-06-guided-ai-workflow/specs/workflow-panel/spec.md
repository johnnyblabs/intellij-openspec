## ADDED Requirements

### Requirement: Workflow Action Panel Display

The tool window SHALL display a Workflow Action Panel between the tree and the status bar that shows the active change's name, status, and artifact progress.

#### Scenario: Active change with ready artifacts
- **WHEN** an active change exists with ready artifacts
- **THEN** the panel SHALL display the change name, a progress indicator (e.g., "2/4 artifacts"), and a "Generate [artifact-name]" button for the next ready artifact

#### Scenario: No active change
- **WHEN** no active change exists
- **THEN** the panel SHALL display guidance text such as "Create a change to get started"

#### Scenario: All artifacts complete
- **WHEN** all artifacts for the active change are done
- **THEN** the panel SHALL display "All artifacts complete" with guidance to apply or archive

### Requirement: Generate Button with Smart Default

The panel SHALL provide a Generate button that uses the user's preferred delivery method by default, with a dropdown to switch methods.

#### Scenario: One-click generation with preferred method
- **WHEN** the user has a saved preferred delivery method
- **THEN** clicking the Generate button SHALL trigger generation using that method without showing a dialog

#### Scenario: Split button dropdown
- **WHEN** the user clicks the dropdown chevron on the Generate button
- **THEN** a menu SHALL appear listing all available delivery methods (Direct API, Copy for [detected tool], Copy to Clipboard, Open in Editor)

#### Scenario: Method remembered
- **WHEN** the user selects a delivery method from the dropdown
- **THEN** that method SHALL be saved as the preferred method for future clicks

### Requirement: Auto-Advance After Generation

The panel SHALL automatically advance to the next ready artifact after a generation completes.

#### Scenario: Direct API generation completes
- **WHEN** an artifact is successfully generated via Direct API
- **THEN** the panel SHALL refresh the DAG status and show the next ready artifact

#### Scenario: Clipboard or editor mode completion
- **WHEN** an artifact prompt is copied to clipboard or opened in editor
- **THEN** the panel SHALL show a "Done — check for updates" link that re-checks artifact status when clicked
