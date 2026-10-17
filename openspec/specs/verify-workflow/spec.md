# Verify Workflow

## Purpose
Pre-archive verification checking completeness, correctness, and coherence of a change's implementation against its artifacts.

## Requirements

### Requirement: Pre-archive verification

The plugin SHALL provide a Verify action that drives off the resolved workflow schema context (`openspec status` `actionContext.mode`). For a non-default mode such as `workspace-planning`, Verify SHALL explain that repo-local verification does not apply and stop without producing spec-driven-shaped findings. For the `spec-driven`, repo-local case, Verify SHALL check a change across two dimensions: **completeness** (deterministic) and **correctness/coherence** (semantic, language-agnostic, delegated to the AI bridge). The completeness dimension SHALL source its artifact-level check from the CLI status DAG (`openspec status --change <name> --json`, schema-aware) when available, and its task-checkbox check from parsing `tasks.md` locally. Verify SHALL NOT gate correctness on source file extension or language.

#### Scenario: Mode gate — non-default mode
- **WHEN** the resolved schema context reports a non-default mode (e.g. `workspace-planning`)
- **THEN** Verify SHALL explain that repo-local verification does not apply and SHALL stop without scanning for spec-driven findings

#### Scenario: Completeness check — artifact level from status
- **WHEN** Verify runs for a spec-driven change and the CLI status DAG is available
- **THEN** it SHALL derive artifact-level completeness from the schema's own artifact set as reported by `openspec status` — treating each artifact the DAG reports as not done as a completeness finding — rather than checking a hardcoded artifact list against the filesystem
- **AND** its completeness verdict SHALL be consistent with the Apply gate by consuming the same orchestration seam, which passes the CLI's artifact status and `isComplete` through unchanged

#### Scenario: Completeness check — task level from tasks.md
- **WHEN** Verify runs for a spec-driven change
- **THEN** it SHALL check, locally and deterministically, that `tasks.md` has no not-done checkboxes — neither incomplete (`- [ ]`) nor in-progress (`- [~]`) — treating any not-done task as a completeness finding, regardless of whether the artifact-level check came from the status DAG or the fallback

#### Scenario: Completeness fallback without usable status
- **WHEN** the CLI status DAG is unavailable for the completeness check (CLI missing, below the supported floor, or its output cannot be used)
- **THEN** Verify SHALL fall back to the deterministic filesystem existence check for the required artifacts and SHALL still perform the `tasks.md` checkbox check
- **AND** Verify SHALL NOT fail or block solely because status was unavailable

#### Scenario: Task lines are recognized with the client's task-line grammar
- **WHEN** `tasks.md` (or any surface that counts a change's tasks: the change node's `X/Y` progress, the workflow panel, Apply) contains checkbox lines
- **THEN** the plugin SHALL recognize a task line exactly as the OpenSpec client (1.13.1+) does: optional leading whitespace, then a bullet marker (`-`, `*`, `+`) or an ordered marker (one to nine digits followed by `.` or `)`), then a checkbox that is either empty (`[]`), whitespace-only (`[ ]`), or holds a single non-space mark with optional surrounding spaces (`[x]`, `[ x ]`, `[~]`)
- **AND** a task SHALL count as done only when its mark is `x` or `X`; every other recognized task (empty, whitespace-only, `~`, or any other single mark) SHALL count toward the total as not-done, so the plugin's completed/total counts equal the client's `completedTasks`/`totalTasks` for the same file

#### Scenario: Partial tasks count as not-done
- **WHEN** `tasks.md` contains in-progress checkboxes written as `- [~]`
- **THEN** Verify SHALL count each `- [~]` toward the total task count and treat it as not-done (blocking archive, with the same gating effect as `- [ ]`), and SHALL NOT exclude it from either the completed or the total count
- **AND** the completeness finding SHALL account for in-progress tasks distinctly (e.g. reporting an in-progress count) rather than dropping them silently

#### Scenario: Correctness and coherence — semantic and language-agnostic
- **WHEN** Verify runs for a spec-driven change with delta specs and/or `design.md`
- **THEN** it SHALL assess whether the implementation satisfies the delta-spec requirements and stays coherent with design decisions by delegating a semantic check to the AI bridge, and SHALL NOT restrict the assessment to any single language or file extension

#### Scenario: Language-agnostic
- **WHEN** the project's implementation is in a non-Java language (e.g. Kotlin, Go)
- **THEN** Verify SHALL evaluate correctness without filtering to `.java` files, so findings are not skewed by the implementation language

#### Scenario: AI provider not configured
- **WHEN** correctness/coherence is requested but no AI provider is configured
- **THEN** Verify SHALL still run the completeness check and SHALL report correctness/coherence as "not assessed (AI provider not configured)" rather than a false pass or fail

### Requirement: Verification report

The plugin SHALL display verification results in the three-state Verify dialog with per-category
sections (Completeness, Validation, Spec Sync), each finding shown with its severity. The archive
gate SHALL be driven by the three-state classification: only a hard validation **BLOCK** disables
archiving; an **IN PROGRESS** change (incomplete tasks, structurally valid) is archivable-anyway
(soft, bypassable), and a **READY** change archives directly.

#### Scenario: Report display
- **WHEN** verification completes
- **THEN** the plugin SHALL show the Verify dialog with a state header (ready/in-progress/blocked) and per-category sections, each finding with a severity-coded label

#### Scenario: Archive gate
- **WHEN** the result classifies as BLOCKED (a validation error)
- **THEN** the plugin SHALL prevent archiving (OK disabled) and show the count of blocking issues
- **AND WHEN** the result classifies as IN PROGRESS (incomplete tasks only)
- **THEN** the plugin SHALL still permit archiving via an "Archive anyway" action rather than blocking, without rendering the change as an error

#### Scenario: Clean report
- **WHEN** no issues are found
- **THEN** the plugin SHALL display a "ready to archive" state with an enabled Archive action

### Requirement: Single Verify surface with three archive-readiness states

The plugin SHALL expose ONE pre-archive **Verify** surface — a single tool-window button, the
`OpenSpec.Verify` menu action, and the Archive pre-flight all running the identical check — and SHALL
NOT present a separate "Compliance" surface. The check SHALL fold three dimensions (completeness,
validation, spec-sync) into one result and classify it into one of three states that mirror upstream
`openspec archive`: **READY**, **IN&nbsp;PROGRESS**, and **BLOCKED**. The two gate conditions carry
the same JSON severity upstream; the plugin SHALL distinguish them by an intrinsic hard/soft property,
not by error count.

#### Scenario: Ready to archive
- **WHEN** the check finds no errors and no incomplete work
- **THEN** the Verify dialog SHALL show a green "ready to archive" header and its OK button SHALL read "Archive" and be enabled

#### Scenario: In progress — soft, bypassable task gate
- **WHEN** the change is structurally valid but has incomplete tasks (the `archive_tasks_incomplete` analogue)
- **THEN** the Verify dialog SHALL show a **neutral** (non-red) "In Progress — N incomplete task(s) remain" header
- **AND** its OK button SHALL read "Archive anyway" and remain **enabled**, mirroring upstream's bypassable `--yes` gate — a valid-but-unfinished change SHALL NOT be rendered as an error/blocked

#### Scenario: Blocked — hard validation gate
- **WHEN** the check finds a validation error (the `archive_validation_failed` analogue)
- **THEN** the Verify dialog SHALL show a red "Validation failed — archive blocked" header and its OK button SHALL be **disabled** (the only state that blocks archive)

#### Scenario: Hard block dominates soft
- **WHEN** a change has both incomplete tasks and a validation error
- **THEN** the state SHALL be BLOCKED (the hard gate dominates), not the neutral in-progress state

#### Scenario: Three-dimension check
- **WHEN** Verify runs for a change
- **THEN** it SHALL evaluate completeness (delegated to the completeness+correctness engine), validation (built-in validator), and spec-sync readiness (unmatched MODIFIED delta targets), mapping each finding to its category and its hard/soft gate kind — completeness findings are soft, a validation error is hard, and spec-sync findings are non-gating warnings

#### Scenario: One surface everywhere
- **WHEN** the user runs Verify from the tool-window button, the `OpenSpec.Verify` menu action, or by archiving
- **THEN** all three SHALL run the same check and present the same three-state Verify dialog, whose OK archives the change (enabled for READY and bypassable IN PROGRESS, disabled for a hard BLOCK)

#### Scenario: Verify status chip
- **WHEN** the workflow panel's Verify status indicator reflects a result
- **THEN** it SHALL show one of three states — "✓ Ready to archive" (green), "In progress · archive anyway" (**neutral**), or "N issue(s) — blocked" (red) — and clicking it SHALL run Verify; it SHALL NOT use the word "compliance"

#### Scenario: Verify notification group
- **WHEN** the plugin registers its notification groups
- **THEN** it SHALL register `OpenSpec.Verify` (STICKY_BALLOON) and SHALL NOT register `OpenSpec.Compliance`
