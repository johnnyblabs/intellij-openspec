# Tree View

## Purpose
Tree-based browsing of specs, changes, and archives with icons, tooltips, search, and theme support.
## Requirements
### Requirement: Tree view display

The plugin SHALL display a tree with Specs, Changes, Archive, and Config sections. Double-clicking navigates to files. The tree auto-refreshes on filesystem changes.

#### Scenario: Tree structure
- **WHEN** the tool window opens on an initialized project
- **THEN** it SHALL show Specs (domains → requirements), Changes (artifacts with status), and Archive sections

#### Scenario: Actionable hints
- **WHEN** no changes exist
- **THEN** a hint node SHALL appear under Changes that triggers Propose on double-click

#### Scenario: Config section in tree
- **WHEN** the tool window opens on an initialized project with `openspec/config.yaml`
- **THEN** the tree SHALL include a Config section after Archive showing flat, read-only key-value entries for top-level config fields (schema, version, profile name, context truncated to ~60 chars, rules count)

#### Scenario: Config section without config file
- **WHEN** no `openspec/config.yaml` exists or the config is not loaded
- **THEN** a hint node SHALL appear under Config indicating no config.yaml was found

#### Scenario: Config node double-click
- **WHEN** the user double-clicks any Config or Config entry node
- **THEN** the plugin SHALL open `openspec/config.yaml` in the editor

#### Scenario: Config entries filtered by search
- **WHEN** the user types a search term matching a config entry label
- **THEN** the Config section SHALL filter to show only matching entries, consistent with existing tree filtering behavior

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

The plugin SHALL provide real-time tree filtering by case-insensitive substring with auto-expand and keyboard shortcut access. Filtering SHALL match a node's label and, for spec content, SHALL also match requirement body text and scenario text so that a term occurring only inside a requirement's prose surfaces its spec and requirement nodes. Content matching SHALL be performed during the off-UI-thread model build over the local OpenSpec files, without persisting a search index.

#### Scenario: Filter behavior
- **WHEN** the user types in the search field
- **THEN** the tree SHALL filter in real-time, auto-expand matches, and restore on clear

#### Scenario: Filter matches requirement body text
- **WHEN** the user types a term that appears in a requirement's body or scenario text but not in any node label
- **THEN** the tree SHALL surface that requirement (and its spec) as a match

### Requirement: HiDPI support

All UI spacing and HTML text widths SHALL use `JBUI.scale()` for proper rendering on HiDPI/Retina displays.

#### Scenario: Scaled rendering
- **WHEN** the plugin runs on a HiDPI display
- **THEN** all text widths and spacing SHALL scale proportionally

### Requirement: Config node types

The tree model SHALL define `CONFIG` and `CONFIG_ENTRY` node types for the config section header and its key-value leaf nodes respectively.

#### Scenario: Config node rendering
- **WHEN** the Config section renders in the tree
- **THEN** the CONFIG node SHALL display with a settings gear icon (`AllIcons.General.Settings`) and CONFIG_ENTRY nodes SHALL display as plain text without icons

#### Scenario: Null or empty config fields
- **WHEN** a top-level config field is null or empty
- **THEN** the corresponding CONFIG_ENTRY node SHALL be omitted from the tree

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

### Requirement: Artifact status badge overlays

The tree SHALL render a change artifact's status as a small badge overlaid on the node's icon, rather than as a glyph prefixed to the node's label. Badges SHALL be applied only to nodes that carry client-owned status: change-artifact nodes, missing-artifact nodes, and change nodes. Spec, requirement, delta-spec-file, and config nodes SHALL NOT carry a status badge. The badge status vocabulary SHALL mirror the OpenSpec CLI's artifact model — done, ready, and blocked — plus a not-created state for a missing artifact. Badges SHALL be distinguishable without relying on color alone, SHALL be theme- and HiDPI-correct, and each badged node SHALL name its status in its tooltip. Artifact ready-versus-blocked status SHALL be sourced from the OpenSpec CLI; when the CLI is unavailable, the tree MAY degrade to a single not-done badge (done versus not-done) rather than fabricating the ready/blocked distinction.

#### Scenario: Change artifact shows its status badge
- **WHEN** a change's proposal/design/tasks/specs artifact node is displayed and the CLI reports its status
- **THEN** the node's icon SHALL carry a badge indicating done, ready, or blocked

#### Scenario: Missing artifact shows a not-created badge
- **WHEN** a required artifact has not been created
- **THEN** its node SHALL carry a not-created badge

#### Scenario: Spec and requirement nodes are never badged
- **WHEN** a spec node or a requirement node is displayed
- **THEN** it SHALL NOT carry any status, completion, or coverage badge

#### Scenario: Status is named in the tooltip
- **WHEN** the user hovers over a badged node
- **THEN** the tooltip SHALL name the status (for example "Complete", "Ready to generate", or "Blocked by: …")

#### Scenario: CLI unavailable degrades gracefully
- **WHEN** the OpenSpec CLI is unavailable
- **THEN** artifact badges MAY collapse ready/blocked to a single not-done badge, and the tree SHALL NOT fabricate a ready-versus-blocked distinction

### Requirement: Change node status rollup

A change node SHALL carry a done badge when all of its artifacts are complete (the change is apply-ready), and SHALL surface the change's task progress as an `X/Y` count in its label when a tasks artifact exists. The change rollup SHALL reflect only client-owned state (artifact completion and task counts as reported by the OpenSpec CLI) and SHALL NOT invent a lifecycle state the client does not define.

#### Scenario: Apply-ready change shows a done badge
- **WHEN** all of a change's artifacts are complete
- **THEN** the change node SHALL carry a done badge

#### Scenario: Task progress shown on the change node
- **WHEN** a change has a tasks artifact with counted checkboxes
- **THEN** the change node's label SHALL include the completed/total task count

