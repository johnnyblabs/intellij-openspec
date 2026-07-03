## MODIFIED Requirements

### Requirement: Pre-archive verification

The plugin SHALL provide a Verify action that drives off the resolved workflow schema context (`openspec status` `actionContext.mode`). For a non-default mode such as `workspace-planning`, Verify SHALL explain that repo-local verification does not apply and stop without producing spec-driven-shaped findings. For the `spec-driven`, repo-local case, Verify SHALL check a change across two dimensions: **completeness** (local, deterministic) and **correctness/coherence** (semantic, language-agnostic, delegated to the AI bridge). Verify SHALL NOT gate correctness on source file extension or language.

#### Scenario: Mode gate — non-default mode
- **WHEN** the resolved schema context reports a non-default mode (e.g. `workspace-planning`)
- **THEN** Verify SHALL explain that repo-local verification does not apply and SHALL stop without scanning for spec-driven findings

#### Scenario: Completeness check
- **WHEN** Verify runs for a spec-driven change
- **THEN** it SHALL check, locally and deterministically, that all required artifacts exist and that `tasks.md` has no not-done checkboxes — neither incomplete (`- [ ]`) nor in-progress (`- [~]`) — treating missing artifacts or any not-done task as completeness findings

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
