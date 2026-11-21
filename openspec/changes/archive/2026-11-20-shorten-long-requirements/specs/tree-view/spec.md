## MODIFIED Requirements

### Requirement: Artifact status badge overlays

The tree SHALL render an artifact's status as a badge overlaid on the node's icon, not a label glyph, on change, change-artifact and missing-artifact nodes only. Spec, requirement, delta-spec-file and config nodes SHALL NOT carry one. The vocabulary SHALL be done, ready, blocked and not-created. Badges SHALL be distinguishable without color alone, theme- and HiDPI-correct, and named in the tooltip. Ready versus blocked SHALL come from the CLI; without it, the tree MAY show done versus not-done.

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

A change node SHALL carry a done badge when all its artifacts are complete, and a distinct archive-ready badge when, in addition, every task is done (a non-zero total, all completed). It SHALL show its task progress as `X/Y` in its label when a tasks artifact exists. The rollup SHALL use only client-owned state reported by the CLI and SHALL NOT invent a lifecycle state. Archive-ready SHALL be presented as a recommendation, never as a gate.

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

#### Scenario: Archive-ready is a recommendation
- **WHEN** a change is archive-ready
- **THEN** the condition is the same as the archive open-tasks warning, a combination of client-owned signals
- **AND** archiving MAY still be refused for other reasons, such as spec validation
