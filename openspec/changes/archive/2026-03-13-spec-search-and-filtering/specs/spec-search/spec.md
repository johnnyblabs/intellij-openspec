## ADDED Requirements

### Requirement: Real-time tree filtering

The tool window SHALL provide a search field that filters tree nodes in real-time as the user types, using case-insensitive substring matching on node labels.

#### Scenario: Filter matches spec domain
- **WHEN** the user types "plugin" in the search field
- **THEN** the tree SHALL show only spec domains whose name contains "plugin" (and their parent "Specs" node)
- **AND** non-matching domains and their children SHALL be hidden

#### Scenario: Filter matches requirement
- **WHEN** the user types "config" in the search field
- **THEN** requirements containing "config" in their label SHALL be visible
- **AND** their parent domain and "Specs" ancestor nodes SHALL also be visible

#### Scenario: Filter matches change name
- **WHEN** the user types a substring matching an active change name
- **THEN** that change and its artifacts SHALL be visible under the "Changes" node

#### Scenario: Filter is case-insensitive
- **WHEN** the user types "SPEC" in the search field
- **THEN** nodes with labels containing "spec", "Spec", or "SPEC" SHALL all match

#### Scenario: Filter debouncing
- **WHEN** the user types multiple characters rapidly
- **THEN** the tree SHALL rebuild at most once per 150ms to avoid excessive updates

### Requirement: Filter empty state

The tool window SHALL display a "no results" hint when the active filter matches no tree nodes.

#### Scenario: No matching nodes
- **WHEN** the user types a filter string that matches no nodes
- **THEN** the tree SHALL display a single hint node: "No results for '<query>'"

### Requirement: Filter clearing restores full tree

Clearing the search field SHALL restore the full unfiltered tree and the expansion state that existed before the filter was applied.

#### Scenario: Clear filter via clear button
- **WHEN** the user clears the search field using the clear button
- **THEN** the full unfiltered tree SHALL be displayed
- **AND** the tree expansion state SHALL match what it was before filtering began

#### Scenario: Clear filter via emptying text
- **WHEN** the user deletes all text from the search field
- **THEN** the full unfiltered tree SHALL be displayed

### Requirement: Auto-expand filtered tree

When a filter is active, all visible nodes in the filtered tree SHALL be expanded so that matching nodes are immediately visible without manual expansion.

#### Scenario: Filtered tree is fully expanded
- **WHEN** a filter is active and the tree is rebuilt with matching nodes
- **THEN** all nodes in the filtered tree SHALL be expanded

### Requirement: Keyboard shortcut to focus search

The user SHALL be able to focus the search field using a keyboard shortcut.

#### Scenario: Focus search with keyboard
- **WHEN** the user presses Ctrl+F (Windows/Linux) or Cmd+F (macOS) while the tree has focus
- **THEN** the search field SHALL receive keyboard focus
