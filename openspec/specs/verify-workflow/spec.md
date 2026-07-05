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
- **AND** its completeness verdict SHALL be consistent with the Apply gate by consuming the same orchestration seam (including the client-side scaffolding adjustments to `isComplete`)

#### Scenario: Completeness check — task level from tasks.md
- **WHEN** Verify runs for a spec-driven change
- **THEN** it SHALL check, locally and deterministically, that `tasks.md` has no not-done checkboxes — neither incomplete (`- [ ]`) nor in-progress (`- [~]`) — treating any not-done task as a completeness finding, regardless of whether the artifact-level check came from the status DAG or the fallback

#### Scenario: Completeness fallback without usable status
- **WHEN** the CLI status DAG is unavailable for the completeness check (CLI missing, below the supported floor, or its output cannot be used)
- **THEN** Verify SHALL fall back to the deterministic filesystem existence check for the required artifacts and SHALL still perform the `tasks.md` checkbox check
- **AND** Verify SHALL NOT fail or block solely because status was unavailable

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

The plugin SHALL display verification results with severity levels (CRITICAL, WARNING, SUGGESTION) and navigation links to relevant files.

#### Scenario: Report display
- **WHEN** verification completes
- **THEN** the plugin SHALL show a report dialog with findings grouped by dimension, each with severity icon, description, and file link

#### Scenario: Archive gate
- **WHEN** the report contains CRITICAL findings
- **THEN** the plugin SHALL warn the user before allowing archive and display the count of critical issues

#### Scenario: Clean report
- **WHEN** no issues are found
- **THEN** the plugin SHALL display "All clear — ready to archive" with a direct Archive button

