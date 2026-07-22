## ADDED Requirements

### Requirement: Rendered preview pane

The plugin SHALL present a read-only rendered-markdown preview pane beside the Browse tree, in a horizontal splitter whose proportion is persisted and whose preview pane MAY be collapsed. Selecting a previewable node SHALL render that node's document into the pane; the pane SHALL remain read-only, and double-clicking a node SHALL continue to open the underlying file in the editor. Rendering SHALL run off the UI thread and update the pane on the UI thread.

#### Scenario: Selection renders the document
- **WHEN** the user single-clicks a previewable node in the Browse tree
- **THEN** the preview pane SHALL render that node's markdown as themed HTML, read-only

#### Scenario: Double-click still opens the file
- **WHEN** the user double-clicks a node backed by a file
- **THEN** the plugin SHALL open that file in the editor, unchanged by the presence of the preview pane

#### Scenario: No previewable selection
- **WHEN** no node is selected, or the selected node has no previewable document
- **THEN** the preview pane SHALL show a placeholder empty state rather than stale content

### Requirement: Source-faithful rendering per node type

The preview SHALL render the source markdown of the selected node — a main spec (`specs/<capability>/spec.md`), a change artifact (`proposal.md`, `design.md`, or `tasks.md`), or a change's delta spec (`changes/<change>/specs/<capability>/spec.md`) — and SHALL NOT reconstruct the document from the CLI's curated JSON. A main spec and a delta spec SHALL each be interpreted according to their own structure and never conflated.

#### Scenario: Main spec rendered
- **WHEN** a main spec node is selected
- **THEN** the preview SHALL render that spec file's markdown

#### Scenario: Change artifact rendered
- **WHEN** a change's proposal, design, or tasks node is selected
- **THEN** the preview SHALL render that artifact file's markdown

#### Scenario: Delta spec rendered as a delta
- **WHEN** a change's delta-spec node is selected
- **THEN** the preview SHALL render the delta-spec markdown, treated as a change's proposed deltas and never as a main spec

### Requirement: Delta operation badges

When rendering a delta spec, the preview SHALL visually badge the delta operation headers — `ADDED`, `MODIFIED`, `REMOVED`, and `RENAMED` — so the change's proposed operations are distinguishable at a glance.

#### Scenario: Operation headers are badged
- **WHEN** a delta spec containing `## ADDED Requirements` (or MODIFIED/REMOVED/RENAMED) is rendered
- **THEN** the preview SHALL present each operation header with a distinct visual badge

### Requirement: Requirement-section anchoring

When a Requirement node is selected, the preview SHALL scroll to that requirement's section within the rendered spec rather than resetting to the top.

#### Scenario: Preview scrolls to the selected requirement
- **WHEN** the user selects a Requirement node whose parent spec is rendered in the preview
- **THEN** the preview SHALL bring that requirement's section into view

### Requirement: Client-owned state only

The preview SHALL reflect only client-owned state. It SHALL NOT display per-spec coverage, score, completion, or status (specs carry none); SHALL NOT present code-to-spec links; SHALL NOT persist a search index or any derived artifact into the OpenSpec tree; and SHALL NOT synthesize a post-archive "effective" spec and present it as spec truth. Change-owned state that the preview surfaces (a change's delta assembly, task progress, or completion) SHALL be sourced from the OpenSpec CLI rather than recomputed.

#### Scenario: No spec status or coverage shown
- **WHEN** a main spec is rendered
- **THEN** the preview SHALL NOT display any per-spec status, completion, or coverage indicator

#### Scenario: Change state comes from the CLI
- **WHEN** the preview surfaces a change's delta assembly, task progress, or completion
- **THEN** those values SHALL be sourced from the OpenSpec CLI, not hand-recomputed from files
