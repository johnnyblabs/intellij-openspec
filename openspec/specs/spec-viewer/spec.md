# spec-viewer Specification

## Purpose
Defines the read-only rendered-markdown preview surface beside the Browse tree — how specs, change artifacts, and delta specs are rendered per node type, how delta operations are badged, how requirement selection anchors the preview, and the on-model boundaries the preview must respect (render source markdown; reflect only client-owned state; never synthesize spec truth or show per-spec status/coverage). Complements the `tree-view` capability, which owns the tree and its search/filter.

## Requirements

### Requirement: Rendered preview pane

The plugin SHALL present a read-only preview pane beside the Browse tree, in a horizontal splitter whose proportion is persisted and whose preview pane MAY be collapsed. Selecting a change or any node under it SHALL render that change's consolidated deltas view, and selecting a delta spec SHALL render that delta spec, into the pane. The pane SHALL remain read-only, and double-clicking a node SHALL continue to open the underlying file in the editor. Rendering SHALL run off the UI thread and update the pane on the UI thread.

#### Scenario: Selection renders the document
- **WHEN** the user single-clicks a change, an artifact of a change or a delta spec in the Browse tree
- **THEN** the preview pane SHALL render, read-only and themed, the change's consolidated deltas view for a change or artifact, or the delta spec's markdown for a delta spec

#### Scenario: Double-click still opens the file
- **WHEN** the user double-clicks a node backed by a file
- **THEN** the plugin SHALL open that file in the editor, unchanged by the presence of the preview pane

#### Scenario: No previewable selection
- **WHEN** no node is selected, or the selected node is not a change, an artifact of a change or a delta spec
- **THEN** the preview pane SHALL show the explanatory empty state rather than stale content

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

The preview SHALL reflect only client-owned state. It SHALL NOT display per-spec coverage, score, completion, or status (specs carry none); SHALL NOT present code-to-spec links; SHALL NOT persist a search index or any derived artifact into the OpenSpec tree; and SHALL NOT synthesize a post-archive "effective" spec and present it as spec truth. Change-owned state that the preview surfaces (a change's delta assembly, validation verdict, task progress, or completion) SHALL be sourced from the OpenSpec CLI rather than recomputed. The one exception is a change's validation when no CLI is available: the preview MAY then show the plugin's built-in check, labelled as the built-in check.

#### Scenario: No spec status or coverage shown
- **WHEN** a main spec is rendered
- **THEN** the preview SHALL NOT display any per-spec status, completion, or coverage indicator

#### Scenario: Change state comes from the CLI
- **WHEN** the preview surfaces a change's delta assembly, validation verdict, task progress, or completion
- **THEN** those values SHALL be sourced from the OpenSpec CLI, not hand-recomputed from files

#### Scenario: Built-in check only without the CLI
- **WHEN** no OpenSpec CLI is available and a change is selected
- **THEN** any validation shown SHALL be the plugin's built-in check, labelled as the built-in check and never as the CLI's verdict

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
- **WHEN** the OpenSpec CLI is unavailable, meaning none is detected or it can't be run, and a change node is selected
- **THEN** the view SHALL show a placeholder indicating the consolidated deltas require the CLI, rather than hand-assembling the delta set

#### Scenario: CLI rejects the change
- **WHEN** the OpenSpec CLI runs for a selected change but returns no usable answer: an error envelope, unreadable output, or a non-zero exit
- **THEN** the view SHALL show a "Can't read this change" placeholder that quotes the CLI's reason, distinct from the CLI-unavailable placeholder
- **AND** it SHALL NOT tell the user to install or configure the CLI
- **AND** it SHALL NOT cache that result, so the next selection asks the CLI again

#### Scenario: Reason from a CLI without a JSON envelope
- **WHEN** the CLI reports the failure only on standard error, as CLI 1.3 does with `✖ Error: …`
- **THEN** the quoted reason SHALL be that error line without its prefix
- **AND** the CLI's "Ignoring flags not applicable" warning SHALL NOT be shown as the reason

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

### Requirement: Every node under a change previews the change's deltas

Selecting a change's artifact node SHALL render that change's consolidated deltas view, whatever the artifact's status, exactly as selecting the change node does. The view SHALL resolve the change name and directory from the enclosing change node. A delta-spec node SHALL render its own markdown as a delta, badged, without requiring the CLI. A main spec SHALL open in the editor, not in the preview pane. The preview SHALL never read an artifact's glob output path as a file.

#### Scenario: Selecting an artifact shows its change's deltas
- **WHEN** the user selects a change's proposal, design, tasks or specs artifact
- **THEN** the preview SHALL render that change's consolidated deltas view, sourced from the CLI as for the change node

#### Scenario: An artifact that does not exist yet
- **WHEN** the user selects a change's artifact that is ready or blocked, with no file yet
- **THEN** the preview SHALL render that change's consolidated deltas view, not the nothing-selected empty state

#### Scenario: Single-file artifact offers to open it
- **WHEN** the selected artifact is backed by a single existing file
- **THEN** the view SHALL show, directly under its header, a link that opens that file in the editor
- **AND** an artifact whose output path is a glob, or whose file does not exist, SHALL show no such link

#### Scenario: Delta spec rendered as a delta
- **WHEN** a change's delta-spec node is selected
- **THEN** the preview SHALL render the delta-spec markdown with badged operation headers, treated as a change's proposed deltas and never as a main spec

#### Scenario: Main spec opens in the editor
- **WHEN** the user opens a main spec
- **THEN** it SHALL open in the editor (with the platform Markdown preview), not in the tool-window preview pane

#### Scenario: Without the CLI
- **WHEN** the OpenSpec CLI is unavailable and the user selects a change's artifact
- **THEN** the preview SHALL show the CLI-unavailable placeholder for that change, with the open-in-editor link when the artifact is a single existing file

### Requirement: The preview pane states its purpose

Every state of the preview pane SHALL start with a "Change Deltas" header, naming the selected change when there is one, and one line saying the view shows what the change will add, modify or remove in the specs when it's archived. With nothing selected, the pane SHALL explain what it shows and how to use it, and SHALL offer to start Propose when the project has no active changes.

#### Scenario: Header in every state
- **WHEN** the preview pane shows deltas, a change with no deltas, the CLI-unavailable placeholder, the can't-read-this-change placeholder, or nothing selected
- **THEN** the pane SHALL start with the "Change Deltas" header and the one-line purpose
- **AND** the header SHALL name the selected change when one is selected

#### Scenario: Nothing selected explains the pane
- **WHEN** nothing previewable is selected
- **THEN** the pane SHALL explain what a change is and what the pane shows
- **AND** it SHALL say that a single click on a change shows its deltas and a double click on a file opens it

#### Scenario: No changes offers Propose
- **WHEN** nothing is selected and the project has no active changes
- **THEN** the empty state SHALL offer a link that starts Propose

### Requirement: Change validation line

When a change is selected and the OpenSpec CLI shows its deltas, the Change Deltas pane SHALL show that change's validation verdict from the CLI under the header, with a link that runs the Validate action for that change. The verdict SHALL match what Validate reports for that change, SHALL be refreshed whenever a file under the OpenSpec directory changes, and SHALL NOT be stored. When the CLI rejects the change or returns no verdict, the line SHALL be left out.

#### Scenario: A valid change
- **WHEN** a selected change validates with no errors
- **THEN** the pane SHALL say the change passed Validate

#### Scenario: A change with errors
- **WHEN** a selected change fails validation
- **THEN** the pane SHALL show the number of errors and warnings Validate reports

#### Scenario: The link runs Validate for the change
- **WHEN** the user activates the validation line's link
- **THEN** the plugin SHALL run Validate for that change only, reporting where Validate always reports

#### Scenario: The verdict follows the files
- **WHEN** a file in the change or a main spec changes after the verdict was shown
- **THEN** selecting the change again SHALL show a verdict from a new validation run

#### Scenario: No CLI
- **WHEN** no OpenSpec CLI is available and a change is selected
- **THEN** the pane SHALL show the built-in check's result labelled "Built-in check"

#### Scenario: The CLI can't answer
- **WHEN** the CLI rejects the change, or its validate run fails or returns no verdict
- **THEN** the pane SHALL show no validation line

### Requirement: Delta operation legend

When the selected change has at least one delta, the Change Deltas pane SHALL show one line saying what archive does with each operation: ADDED adds the requirement to the main spec, MODIFIED replaces the whole requirement and so must keep its scenarios, REMOVED deletes it, and RENAMED renames it and keeps its text.

#### Scenario: A change with deltas
- **WHEN** the selected change has at least one delta
- **THEN** the pane SHALL show the legend once

#### Scenario: A change without deltas
- **WHEN** the selected change has no deltas
- **THEN** the pane SHALL NOT show the legend
