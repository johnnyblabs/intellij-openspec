## ADDED Requirements

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
