## ADDED Requirements

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
- **WHEN** the preview pane shows deltas, a change with no deltas, the CLI-unavailable placeholder, or nothing selected
- **THEN** the pane SHALL start with the "Change Deltas" header and the one-line purpose
- **AND** the header SHALL name the selected change when one is selected

#### Scenario: Nothing selected explains the pane
- **WHEN** nothing previewable is selected
- **THEN** the pane SHALL explain what a change is and what the pane shows
- **AND** it SHALL say that a single click on a change shows its deltas and a double click on a file opens it

#### Scenario: No changes offers Propose
- **WHEN** nothing is selected and the project has no active changes
- **THEN** the empty state SHALL offer a link that starts Propose

## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Source-faithful rendering per node type
**Reason**: Change artifacts no longer render their own markdown in the pane; they preview their change's deltas. The delta-spec and main-spec rules move to "Every node under a change previews the change's deltas".
**Migration**: Double-click an artifact, or use the view's open-in-editor link, to read its markdown in the editor.

### Requirement: A change's specs artifact previews its deltas
**Reason**: Folded into "Every node under a change previews the change's deltas", which routes every artifact, including the specs artifact and any other glob artifact, to its change's deltas and never reads a glob as a file.
**Migration**: None; the specs artifact keeps showing its change's deltas.
