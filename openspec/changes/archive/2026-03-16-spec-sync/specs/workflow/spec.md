## MODIFIED Requirements

### Requirement: Workflow action panel

The plugin SHALL display the selected change's pipeline status with a compact artifact pipeline, tool/delivery selector, action buttons, and a Sync Specs button that appears when all artifacts are complete and delta specs exist.

#### Scenario: Pipeline visualization
- **WHEN** a change is selected
- **THEN** the panel SHALL show artifact status chips (DONE, READY, BLOCKED) with content-aware scaffolding detection

#### Scenario: Generate button
- **WHEN** the user clicks Generate
- **THEN** it SHALL deliver via the selected method (clipboard, editor tab, or Direct API) with tool-specific post-delivery guidance

#### Scenario: Sync Specs button visibility
- **WHEN** all artifacts are complete and the change contains delta spec sections
- **THEN** the panel SHALL show a Sync Specs button alongside the Verify and Archive buttons

#### Scenario: Sync Specs button hidden
- **WHEN** the change has no delta spec sections
- **THEN** the Sync Specs button SHALL NOT be visible
