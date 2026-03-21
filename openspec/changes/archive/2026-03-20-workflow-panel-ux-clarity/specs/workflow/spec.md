## ADDED Requirements

### Requirement: Tree-to-panel synchronization

The plugin SHALL synchronize the workflow panel's active change with the tree view selection. When the user selects a change node (or a child of a change node) in the tree, the workflow panel SHALL update to display that change's pipeline, icon bar, and status strip. The synchronization SHALL be one-way: tree drives panel.

#### Scenario: Select change in tree
- **WHEN** the user clicks a change node in the tree
- **THEN** the workflow panel SHALL update its active change to match and refresh the pipeline display

#### Scenario: Select child of change node
- **WHEN** the user clicks an artifact or spec node under a change
- **THEN** the workflow panel SHALL update its active change to the parent change

#### Scenario: Select non-change node
- **WHEN** the user clicks a node that is not a change or child of a change (e.g., main specs, config, archive)
- **THEN** the workflow panel SHALL NOT change its active change selection

#### Scenario: Dropdown remains functional
- **WHEN** the user selects a change from the workflow panel's dropdown
- **THEN** the panel SHALL update normally without affecting the tree selection
