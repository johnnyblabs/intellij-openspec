# spec-viewer Specification

## Purpose
Defines the read-only rendered-markdown preview surface beside the Browse tree — how specs, change artifacts, and delta specs are rendered per node type, how delta operations are badged, how requirement selection anchors the preview, and the on-model boundaries the preview must respect (render source markdown; reflect only client-owned state; never synthesize spec truth or show per-spec status/coverage). Complements the `tree-view` capability, which owns the tree and its search/filter.
## Requirements
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

### Requirement: Consolidated change-deltas view

When a change node is selected in the Browse tree, the preview SHALL render a consolidated, read-only view of that change's spec-level deltas. The assembled delta set SHALL be sourced from the OpenSpec CLI (`openspec show <change>` as JSON), reading only its standard output, and SHALL NOT be hand-assembled from the change's delta-spec files. The view SHALL group deltas by capability and, within a capability, present them in the CLI's operation order (ADDED, then MODIFIED, then REMOVED, then RENAMED). Capability groups SHALL be ordered by a stable plugin-imposed sort rather than relying on the CLI's cross-capability ordering. Each rendered requirement SHALL show its text and scenarios as reported by the CLI, and each operation SHALL be visually badged consistently with delta-spec rendering.

#### Scenario: Change selection renders its deltas
- **WHEN** the user selects a change node that has spec-level deltas
- **THEN** the preview SHALL render the change's deltas grouped by capability and operation, each operation badged, sourced from the CLI

#### Scenario: Deltas grouped and ordered deterministically
- **WHEN** a change's deltas span multiple capabilities
- **THEN** the view SHALL present capability groups in a stable order and, within each capability, order operations ADDED → MODIFIED → REMOVED → RENAMED

#### Scenario: Renamed requirement is shown without requirement body
- **WHEN** a delta's operation is RENAMED
- **THEN** the view SHALL show the from/to rename without assuming a requirement body, and SHALL NOT error on the absence of requirement text

#### Scenario: Change with no deltas
- **WHEN** a selected change has no spec-level deltas
- **THEN** the view SHALL show an informative empty state rather than an error or a fabricated delta

#### Scenario: CLI unavailable
- **WHEN** the OpenSpec CLI is unavailable and a change node is selected
- **THEN** the view SHALL show a placeholder indicating the consolidated deltas require the CLI, rather than hand-assembling the delta set

### Requirement: Deltas view reflects only client-owned delta state

The consolidated deltas view SHALL reflect only the delta state the OpenSpec CLI reports. It SHALL NOT synthesize a post-application "effective" spec and present it as spec truth, SHALL NOT display per-delta status, progress, coverage, or an "applied" indicator, and SHALL NOT present a computed semantic before/after requirement diff as authoritative CLI output.

#### Scenario: No synthesized effective spec
- **WHEN** a change's deltas are rendered
- **THEN** the view SHALL present them as ADDED/MODIFIED/REMOVED/RENAMED deltas and SHALL NOT render a merged post-apply spec as if it were current spec truth

#### Scenario: No per-delta status
- **WHEN** any delta is rendered
- **THEN** the view SHALL NOT show a status, progress, coverage, or applied/unapplied indicator on the delta

### Requirement: Cross-link from deltas to the delta diff

Each capability section in the consolidated deltas view SHALL offer a link to the existing delta-vs-current-main diff for that capability, so the consolidated reading view and the per-capability machine diff complement each other.

#### Scenario: Capability section links to its diff
- **WHEN** the consolidated deltas view renders a capability that has a delta spec
- **THEN** that capability's section SHALL provide a link that opens the delta-vs-current-main diff for that capability

