## MODIFIED Requirements

### Requirement: Workflow action panel

The plugin SHALL display the selected change's pipeline status with a compact artifact pipeline, tool/delivery selector, and action buttons including Continue, Verify, and Sync Specs.

#### Scenario: Pipeline visualization
- **WHEN** a change is selected
- **THEN** the panel SHALL show artifact status chips (DONE, READY, BLOCKED) with content-aware scaffolding detection

#### Scenario: Generate button
- **WHEN** the user clicks Generate
- **THEN** it SHALL deliver via the selected method (clipboard, editor tab, or Direct API) with tool-specific post-delivery guidance

#### Scenario: Continue button
- **WHEN** the user clicks Continue
- **THEN** the panel SHALL generate the next ready artifact and update the pipeline visualization

#### Scenario: Verify button
- **WHEN** the user clicks Verify
- **THEN** the panel SHALL run pre-archive verification and display the results report

#### Scenario: Sync Specs button
- **WHEN** the user clicks Sync Specs and delta specs exist
- **THEN** the panel SHALL show a preview diff and apply confirmed changes to main specs

## ADDED Requirements

### Requirement: FF action registration

The plugin SHALL register a Fast-Forward action in the OpenSpec menu and toolbar, accessible via menu item and keyboard shortcut.

#### Scenario: Menu registration
- **WHEN** the user opens the OpenSpec menu
- **THEN** Fast-Forward SHALL appear as a menu item with appropriate icon

### Requirement: Bulk Archive action registration

The plugin SHALL register a Bulk Archive action in the OpenSpec menu for archiving multiple changes at once.

#### Scenario: Menu registration
- **WHEN** the user opens the OpenSpec menu
- **THEN** Bulk Archive SHALL appear as a menu item
