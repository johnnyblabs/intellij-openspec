# Verify Workflow

## Purpose
Pre-archive verification checking completeness, correctness, and coherence of a change's implementation against its artifacts.

## Requirements

### Requirement: Pre-archive verification

The plugin SHALL provide a Verify action that checks a change's implementation against its artifacts across three dimensions: completeness, correctness, and coherence.

#### Scenario: Completeness check
- **WHEN** Verify runs
- **THEN** it SHALL parse tasks.md for incomplete checkboxes (`- [ ]`) and check that all required artifacts exist

#### Scenario: Correctness check
- **WHEN** Verify runs and delta specs exist
- **THEN** it SHALL search the codebase for evidence that each spec requirement has been implemented

#### Scenario: Coherence check
- **WHEN** Verify runs and design.md exists
- **THEN** it SHALL cross-reference design decisions against the implementation to detect divergence

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