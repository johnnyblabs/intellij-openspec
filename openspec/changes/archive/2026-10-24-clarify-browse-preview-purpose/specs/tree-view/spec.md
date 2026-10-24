## ADDED Requirements

### Requirement: Changes is expanded on first load

When the tool window builds the Changes tree for the first time and the project has at least one active change, the plugin SHALL expand the `Changes` node so every change row is visible. A later collapse by the user SHALL be kept when the tree is rebuilt.

#### Scenario: First load shows the changes
- **WHEN** the tool window first loads a project with at least one active change
- **THEN** the `Changes` node SHALL be expanded and each change row SHALL be visible

#### Scenario: A user's collapse survives a refresh
- **WHEN** the user collapses `Changes` and the tree is then rebuilt
- **THEN** `Changes` SHALL stay collapsed
