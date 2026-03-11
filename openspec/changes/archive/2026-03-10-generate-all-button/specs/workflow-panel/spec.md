## MODIFIED Requirements

### Requirement: Generate All UI progress feedback

The WorkflowActionPanel SHALL display real-time progress during a Generate All operation.

#### Scenario: Progress label during generation
- **WHEN** a Generate All operation is in progress
- **THEN** the panel SHALL display a progress bar beneath the pipeline chips showing determinate progress, an elapsed time label, and the generate button text SHALL show the current artifact name and count (e.g., "Generating design... 2/4")

#### Scenario: Pipeline chips update in real-time
- **WHEN** an artifact completes during a Generate All chain
- **THEN** the pipeline chip for that artifact SHALL transition to the done state with a brief green flash animation (300ms), and the next artifact's chip SHALL enter a pulsing generating state

#### Scenario: Buttons disabled during generation
- **WHEN** a Generate All operation is in progress
- **THEN** both the Generate and Generate All buttons SHALL be disabled and a Cancel button SHALL be shown

#### Scenario: Cancel button stops generation
- **WHEN** the user clicks Cancel during a Generate All operation
- **THEN** the system SHALL cancel the remaining chain and restore the panel to its normal state showing current progress

#### Scenario: Completion restores normal state
- **WHEN** a Generate All operation completes successfully
- **THEN** the panel SHALL display a completion celebration (green flash, success message, progress bar turning green) before restoring to normal state with all pipeline chips showing done

#### Scenario: Error shows notification and restores state
- **WHEN** a Generate All operation fails on an artifact
- **THEN** the failed artifact chip SHALL display in red with an error icon, an inline error message SHALL appear, and a "Retry" button SHALL be shown

### Requirement: Artifact Pipeline Visualization

The WorkflowActionPanel SHALL display a compact pipeline status row showing the state of each artifact in the change's DAG, using content-aware status that accounts for scaffolding detection. Each chip SHALL be a `JPanel` with tooltip support, click-to-open for completed artifacts, click-to-generate for ready artifacts, and a highlighted border on the current ready artifact.

#### Scenario: Pipeline with mixed states
- **WHEN** the selected change has artifacts in done, ready, and blocked states (after scaffolding override)
- **THEN** the pipeline row SHALL show each artifact as an interactive chip with a visual state indicator (checkmark icon for done, filled circle for ready, empty circle for blocked), a text label, and a tooltip describing the artifact's role

#### Scenario: Pipeline updates after generation
- **WHEN** an artifact generation completes
- **THEN** the pipeline row SHALL refresh to show the updated states

#### Scenario: Scaffolded artifacts show as not done
- **WHEN** an artifact file exists but contains only scaffolding placeholder content
- **THEN** the pipeline chip SHALL show the artifact as ready or blocked (not done)

#### Scenario: Current step is visually prominent
- **WHEN** the pipeline contains a READY artifact
- **THEN** the chip for that artifact SHALL be visually highlighted with a distinct border and background color to indicate it is the current step

#### Scenario: READY chip triggers generation
- **WHEN** the user clicks a READY pipeline chip
- **THEN** the system SHALL trigger generation for that artifact using the selected delivery method

#### Scenario: Generating chip animation
- **WHEN** an artifact is actively being generated during a Generate All operation
- **THEN** the chip SHALL display a pulsing border animation (toggling between bright and dim at 600ms intervals) and use IntelliJ's animated process step icons to indicate active work

#### Scenario: Error chip state
- **WHEN** an artifact fails during Generate All
- **THEN** the chip SHALL display with a red border, red text, and an error icon (AllIcons.General.Error)

### Requirement: Generate All button visibility

The WorkflowActionPanel SHALL display the Generate All button when Direct API is configured and multiple artifacts remain, styled as the primary hero action.

#### Scenario: Generate All button visible and styled
- **WHEN** Direct API is configured and 2 or more artifacts remain to be generated
- **THEN** the panel SHALL display a "Generate All (N)" button with a gradient background, bold font, and an execute icon

#### Scenario: Generate All button hidden without API
- **WHEN** Direct API is not configured
- **THEN** the panel SHALL NOT display the "Generate All" button

#### Scenario: Generate All button hidden with single artifact
- **WHEN** only 1 artifact remains to be generated
- **THEN** the panel SHALL NOT display the "Generate All" button
