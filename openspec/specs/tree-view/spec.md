# Tree View

## Purpose
Tree-based browsing of specs, changes, and archives with icons, tooltips, search, and theme support.
## Requirements
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

### Requirement: HiDPI support

All UI spacing and HTML text widths SHALL use `JBUI.scale()` for proper rendering on HiDPI/Retina displays.

#### Scenario: Scaled rendering
- **WHEN** the plugin runs on a HiDPI display
- **THEN** all text widths and spacing SHALL scale proportionally

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

The tree SHALL render a change artifact's status as a small badge overlaid on the node's icon, rather than as a glyph prefixed to the node's label. Badges SHALL be applied only to nodes that carry client-owned status: change-artifact nodes, missing-artifact nodes, and change nodes. Spec, requirement, delta-spec-file, and config nodes SHALL NOT carry a status badge. The badge status vocabulary SHALL mirror the OpenSpec CLI's artifact model — done, ready, and blocked — plus a not-created state for a missing artifact; a change node additionally MAY carry the archive-ready status defined by the change node status rollup (artifacts complete and all tasks done), which is a combination of client-owned signals, not a new lifecycle state. Badges SHALL be distinguishable without relying on color alone, SHALL be theme- and HiDPI-correct, and each badged node SHALL name its status in its tooltip. Artifact ready-versus-blocked status SHALL be sourced from the OpenSpec CLI; when the CLI is unavailable, the tree MAY degrade to a single not-done badge (done versus not-done) rather than fabricating the ready/blocked distinction.

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

A change node SHALL carry a done badge when all of its artifacts are complete (the change is apply-ready), SHALL instead carry a distinct archive-ready badge when, in addition, every one of its tasks is done (a completed count equal to a non-zero total), and SHALL surface the change's task progress as an `X/Y` count in its label when a tasks artifact exists. The change rollup SHALL reflect only client-owned state (artifact completion and task counts as reported by the OpenSpec CLI) and SHALL NOT invent a lifecycle state the client does not define. Archive-ready is the conjunction of those two client-owned signals — the same condition under which the client's archive raises no open-tasks warning — and SHALL be presented as a recommendation, never as a guarantee or a gate: archiving may still be refused for other reasons, such as spec validation.

#### Scenario: Apply-ready change shows a done badge
- **WHEN** all of a change's artifacts are complete
- **THEN** the change node SHALL carry a done badge

#### Scenario: Task progress shown on the change node
- **WHEN** a change has a tasks artifact with counted checkboxes
- **THEN** the change node's label SHALL include the completed/total task count

#### Scenario: Change with all artifacts complete and all tasks done shows the archive-ready badge
- **WHEN** all of a change's artifacts are complete and its tasks artifact has a non-zero task count with every task done
- **THEN** the change node SHALL carry the archive-ready badge instead of the done badge, distinguishable from it by shape and not by color alone
- **AND** its label SHALL keep the `X/Y` count and add an "all tasks done" text cue after it

#### Scenario: Apply-ready change with open tasks keeps the done badge
- **WHEN** all of a change's artifacts are complete but at least one task is not done, or the change has no counted tasks
- **THEN** the change node SHALL carry the done badge, not the archive-ready badge, and its label SHALL NOT carry the "all tasks done" cue

#### Scenario: All tasks done but an artifact incomplete is not archive-ready
- **WHEN** every task of a change is done but at least one of its artifacts is not complete
- **THEN** the change node SHALL NOT carry the archive-ready badge or the "all tasks done" cue

#### Scenario: Archive-ready is framed as a recommendation
- **WHEN** the user hovers over an archive-ready change node
- **THEN** the tooltip SHALL name the state first, SHALL present archiving as a suggestion rather than a guarantee, and SHALL point to Verify as the authority on whether archiving would succeed

#### Scenario: No archive-ready state without the CLI's artifact signal
- **WHEN** the OpenSpec CLI is unavailable, so artifact completion cannot be determined
- **THEN** the change node SHALL carry neither the done badge nor the archive-ready badge, even when every task is done, and SHALL still show its `X/Y` count

### Requirement: Changes tree display

The plugin SHALL display a tool-window tree showing the **Changes** section — changes with per-artifact status. Navigation to spec files, archived changes, and `config.yaml` is owned by the **Project View** (which already lists `openspec/**` and opens files into the editor), not a second file-navigation tree in the tool window. The Changes tree auto-refreshes on filesystem changes.

#### Scenario: Tree structure
- **WHEN** the tool window opens on an initialized project
- **THEN** it SHALL show the Changes section (changes with per-artifact status); spec, archived-change, and config files are browsed in the Project View, not a tool-window file tree

#### Scenario: Actionable hints
- **WHEN** no changes exist
- **THEN** a hint node SHALL appear under Changes that triggers Propose on double-click

#### Scenario: Change-node visual hierarchy
- **WHEN** a change node renders in the Changes tree
- **THEN** the change name SHALL be shown in the primary (default) color and the `X/Y` task-completion count as dimmed secondary text — so the count does not compete with the name. No change-status tag is shown: OpenSpec has no change-status concept (active vs archived is directory location), and the invented `[proposed]`/`[applied]` tag is retired.
- **AND** a complete count (`N/N`) SHALL NOT itself be colored as "done": the apply-ready and archive-ready states are conveyed by the node's status icon badge, and archive-ready additionally by a separate "all tasks done" text fragment after the still-dimmed count, so the count text never carries the state on its own

### Requirement: Spec content search

The plugin SHALL provide content search over spec requirement bodies and scenario text via a **Search Everywhere contributor** — so a term occurring only inside a requirement's prose surfaces that spec/requirement and, on selection, opens it in the editor at the requirement. The search SHALL run off the UI thread over the local OpenSpec files without persisting a search index, and SHALL be available during indexing (dumb-aware).

#### Scenario: Content search via Search Everywhere
- **WHEN** the user enters, in Search Everywhere, a term that appears in a requirement's body or scenario text but not in any label
- **THEN** the contributor SHALL surface that requirement, and selecting it SHALL open its spec in the editor at that requirement

#### Scenario: Off-thread, index-free content matching
- **WHEN** content search runs
- **THEN** it SHALL scan the local spec files off the UI thread without persisting an index, and SHALL function during indexing

