## ADDED Requirements

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
