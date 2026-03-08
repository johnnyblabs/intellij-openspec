## ADDED Requirements

### Requirement: Pipeline chips display artifact descriptions on hover
Each pipeline chip SHALL display a tooltip describing the artifact's role when the user hovers over it.

#### Scenario: Hover over proposal chip
- **WHEN** the user hovers over the "proposal" pipeline chip
- **THEN** a tooltip SHALL appear showing "Why this change is needed"

#### Scenario: Hover over unknown artifact
- **WHEN** the user hovers over a pipeline chip for an artifact with no known description
- **THEN** the tooltip SHALL show the artifact ID

### Requirement: Clicking a completed chip opens the artifact file
Completed pipeline chips SHALL be clickable, opening the artifact file in the editor.

#### Scenario: Click completed proposal chip
- **WHEN** the user clicks a pipeline chip whose artifact status is DONE
- **THEN** the artifact file SHALL open in the IntelliJ editor via FileEditorManager

#### Scenario: Click non-complete chip
- **WHEN** the user clicks a pipeline chip whose artifact status is READY or BLOCKED
- **THEN** nothing SHALL happen (no editor action)

### Requirement: Right-click context menu on completed chips
Completed pipeline chips SHALL display a context menu with "Open" and "Regenerate" actions on right-click.

#### Scenario: Right-click completed chip shows menu
- **WHEN** the user right-clicks a pipeline chip whose artifact status is DONE
- **THEN** a popup menu SHALL appear with "Open" and "Regenerate" options

#### Scenario: Open action opens file
- **WHEN** the user selects "Open" from the chip context menu
- **THEN** the artifact file SHALL open in the editor

#### Scenario: Regenerate action with no downstream dependents
- **WHEN** the user selects "Regenerate" on an artifact with no completed downstream dependents
- **THEN** the generation flow SHALL start for that artifact using the current delivery method

#### Scenario: Regenerate action with completed downstream dependents
- **WHEN** the user selects "Regenerate" on an artifact that has downstream dependents already completed
- **THEN** a confirmation dialog SHALL warn that downstream artifacts may become inconsistent
- **AND** generation SHALL proceed only if the user confirms

### Requirement: Current artifact chip is visually highlighted
The pipeline SHALL visually distinguish the current ready artifact from completed and blocked ones.

#### Scenario: Ready artifact highlighting
- **WHEN** the pipeline contains a READY artifact
- **THEN** that chip SHALL be visually highlighted with a distinct border or background to indicate it is the current step
