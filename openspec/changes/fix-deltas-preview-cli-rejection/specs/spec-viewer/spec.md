## MODIFIED Requirements

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
