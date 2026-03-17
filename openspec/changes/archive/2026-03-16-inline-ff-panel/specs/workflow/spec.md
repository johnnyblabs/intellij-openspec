## MODIFIED Requirements

### Requirement: Workflow action panel

The plugin SHALL display the selected change's pipeline status with a compact artifact pipeline, tool/delivery selector, action buttons, and a Sync Specs button that appears when all artifacts are complete and delta specs exist. The content area SHALL use CardLayout to manage NO_CHANGES, FF_INPUT, and PIPELINE views, with the tool selector remaining visible across all cards.

#### Scenario: Pipeline visualization
- **WHEN** a change is selected
- **THEN** the panel SHALL show the PIPELINE card with artifact status chips (DONE, READY, BLOCKED) with content-aware scaffolding detection

#### Scenario: Generate button
- **WHEN** the user clicks Generate
- **THEN** it SHALL deliver via the selected method (clipboard, editor tab, or Direct API) with tool-specific post-delivery guidance

#### Scenario: Sync Specs button visibility
- **WHEN** all artifacts are complete and the change contains delta spec sections
- **THEN** the panel SHALL show a Sync Specs button alongside the Verify and Archive buttons

#### Scenario: Sync Specs button hidden
- **WHEN** the change has no delta spec sections
- **THEN** the Sync Specs button SHALL NOT be visible

#### Scenario: FF input activation
- **WHEN** the user clicks the FF toolbar button or "Fast-Forward" hyperlink
- **THEN** the panel SHALL switch to the FF_INPUT card while keeping the tool selector visible

### Requirement: Generate All

The plugin SHALL sequentially generate all remaining artifacts in dependency order via Direct API with progress reporting and cancellation support. Generate All SHALL also be triggered automatically by the FF flow when the selected delivery method is Direct API.

#### Scenario: Orchestration
- **WHEN** Generate All is triggered (manually or via FF with Direct API selected)
- **THEN** artifacts SHALL generate in DAG dependency order with real-time progress feedback
