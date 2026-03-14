# Tree View

## Purpose
Tree-based browsing of specs, changes, and archives with icons, tooltips, search, and theme support.

## Requirements

### Requirement: Tree view display

The plugin SHALL display a tree with Specs, Changes, and Archive sections. Double-clicking navigates to files. The tree auto-refreshes on filesystem changes.

#### Scenario: Tree structure
- **WHEN** the tool window opens on an initialized project
- **THEN** it SHALL show Specs (domains → requirements), Changes (artifacts with status), and Archive sections

#### Scenario: Actionable hints
- **WHEN** no changes exist
- **THEN** a hint node SHALL appear under Changes that triggers Propose on double-click

### Requirement: Icons and theme support

The plugin SHALL provide distinct icons per node type with `_dark.svg` variants. Status colors SHALL use `JBColor` with explicit light/dark pairs for proper contrast.

#### Scenario: Theme-aware rendering
- **WHEN** the IDE switches themes
- **THEN** icons SHALL use dark variants and status colors SHALL remain readable

### Requirement: Tooltips

Each tree node SHALL display a contextual tooltip on hover providing non-visible information (file paths, artifact status, requirement counts).

#### Scenario: Tooltip content
- **WHEN** the user hovers over a tree node
- **THEN** a tooltip SHALL appear with contextual metadata

### Requirement: Search and filtering

The plugin SHALL provide real-time tree filtering by case-insensitive substring with auto-expand and keyboard shortcut access.

#### Scenario: Filter behavior
- **WHEN** the user types in the search field
- **THEN** the tree SHALL filter in real-time, auto-expand matches, and restore on clear

### Requirement: HiDPI support

All UI spacing and HTML text widths SHALL use `JBUI.scale()` for proper rendering on HiDPI/Retina displays.

#### Scenario: Scaled rendering
- **WHEN** the plugin runs on a HiDPI display
- **THEN** all text widths and spacing SHALL scale proportionally
