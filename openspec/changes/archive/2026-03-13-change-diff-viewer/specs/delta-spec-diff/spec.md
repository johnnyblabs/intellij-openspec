## ADDED Requirements

### Requirement: Delta spec diff preview

The plugin SHALL provide a "Preview Diff" action that opens IntelliJ's diff viewer showing a delta spec alongside its corresponding main spec.

#### Scenario: Preview diff for modified capability
- **WHEN** the user selects "Preview Diff" on a delta spec node whose domain has an existing main spec at `openspec/specs/<domain>/spec.md`
- **THEN** the diff viewer SHALL open with the main spec on the left and the delta spec on the right
- **AND** both panels SHALL have descriptive titles identifying which file is shown

#### Scenario: Preview diff for new capability
- **WHEN** the user selects "Preview Diff" on a delta spec node whose domain has no existing main spec
- **THEN** the diff viewer SHALL open with an empty left panel labeled to indicate a new capability
- **AND** the delta spec content SHALL appear on the right

#### Scenario: Diff viewer uses standard IntelliJ UI
- **WHEN** the diff viewer opens for a delta spec preview
- **THEN** it SHALL use IntelliJ's built-in `DiffManager` with `SimpleDiffRequest`
- **AND** standard diff features (navigation, unified/side-by-side toggle) SHALL be available

### Requirement: Delta spec context menu

DELTA_SPEC tree nodes SHALL have a context menu with actions for previewing and opening the file.

#### Scenario: Right-click delta spec node
- **WHEN** the user right-clicks a DELTA_SPEC node in the tree
- **THEN** a context menu SHALL appear with "Preview Diff" and "Open File" actions

#### Scenario: Open file action
- **WHEN** the user selects "Open File" from a delta spec context menu
- **THEN** the delta spec file SHALL open in the editor
