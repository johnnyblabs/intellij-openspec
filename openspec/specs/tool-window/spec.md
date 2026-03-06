# Tool Window

## Domain
Tree-based UI panel for browsing specs, changes, and archives.

## Requirements

### Requirement: Tree View Display

The plugin SHALL provide a tool window that displays OpenSpec project contents in a navigable tree structure.

**Keyword:** SHALL

#### Scenarios

**Scenario: Tree structure**
- GIVEN an OpenSpec project with specs and changes
- WHEN the tool window is opened
- THEN it SHALL display a tree with top-level nodes: Specs, Changes, Archive
- AND Specs node SHALL contain child nodes for each spec domain

### Requirement: File Navigation

The plugin SHALL allow users to navigate to spec files by double-clicking tree nodes.

**Keyword:** SHALL

#### Scenarios

**Scenario: Open spec file**
- GIVEN a spec tree node is visible
- WHEN the user double-clicks the node
- THEN the corresponding file SHALL open in the editor

### Requirement: Auto Refresh

The tool window SHOULD automatically refresh when files in the `openspec/` directory change.

**Keyword:** SHOULD

#### Scenarios

**Scenario: File system change**
- GIVEN the tool window is visible
- WHEN a file is added or modified under `openspec/`
- THEN the tree SHOULD refresh to reflect the change
