## ADDED Requirements

### Requirement: Tree selection drives workflow panel

The tree view SHALL emit change selection events when the user clicks a change node or any descendant of a change node. The tree model SHALL provide a method to resolve the change name from any selected tree node.

#### Scenario: Resolve change name from change node
- **WHEN** the user selects a node representing an active change
- **THEN** the tree model SHALL return the change name for that node

#### Scenario: Resolve change name from child node
- **WHEN** the user selects a node that is a descendant of a change node (e.g., an artifact or spec under the change)
- **THEN** the tree model SHALL walk up the tree and return the parent change name

#### Scenario: Non-change node returns null
- **WHEN** the user selects a node that is not under a change (e.g., main specs, config, archive)
- **THEN** the tree model SHALL return null, indicating no change context
