## MODIFIED Requirements

### Requirement: Archive action

The plugin SHALL move completed changes to `openspec/changes/archive/YYYY-MM-DD-<name>/` only after passing a pre-flight compliance check. If the compliance check finds ERROR-level findings, the archive SHALL be blocked and a dialog SHALL display findings with remediation guidance. If findings are WARNING-level only, the dialog SHALL display them but allow the user to proceed.

#### Scenario: Archive flow
- **WHEN** the user archives a change
- **THEN** the plugin SHALL run `ComplianceService.checkCompliance()` before performing the archive

#### Scenario: Pre-flight compliance gate blocks on errors
- **WHEN** the pre-flight compliance check returns ERROR-level findings
- **THEN** the archive SHALL be blocked and a dialog SHALL display the errors with remediation guidance

#### Scenario: Pre-flight compliance gate allows with warnings
- **WHEN** the pre-flight compliance check returns only WARNING-level findings
- **THEN** the dialog SHALL display the warnings and allow the user to proceed with archive

### Requirement: Workflow action panel

The plugin SHALL display the selected change's pipeline status with a compact artifact pipeline, tool/delivery selector, action buttons, a Sync Specs button that appears when all artifacts are complete and delta specs exist, and a compliance status chip showing the change's compliance state.

#### Scenario: Pipeline visualization
- **WHEN** a change is selected
- **THEN** the panel SHALL show artifact status chips (DONE, READY, BLOCKED) with content-aware scaffolding detection

#### Scenario: Generate button
- **WHEN** the user clicks Generate
- **THEN** it SHALL deliver via the selected method (clipboard, editor tab, or Direct API) with tool-specific post-delivery guidance

#### Scenario: Sync Specs button visibility
- **WHEN** all artifacts are complete and the change contains delta spec sections
- **THEN** the panel SHALL show a Sync Specs button alongside the Verify and Archive buttons

#### Scenario: Sync Specs button hidden
- **WHEN** the change has no delta spec sections
- **THEN** the Sync Specs button SHALL NOT be visible

#### Scenario: Compliance chip displayed
- **WHEN** the workflow action panel renders for a selected change
- **THEN** a compliance status chip SHALL be visible alongside the artifact pipeline chips

## ADDED Requirements

### Requirement: Archive pre-flight dialog
The plugin SHALL display the compliance check results in a dialog before archive proceeds. The dialog SHALL list all compliance findings grouped by category, with ERROR findings highlighted and WARNING findings shown separately. The dialog SHALL have "Archive" and "Cancel" buttons, with "Archive" disabled when ERROR findings exist.

#### Scenario: Error findings block archive
- **WHEN** the pre-flight dialog shows ERROR-level findings
- **THEN** the "Archive" button SHALL be disabled and the dialog SHALL display remediation guidance for each error

#### Scenario: Warning-only findings allow archive
- **WHEN** the pre-flight dialog shows only WARNING-level findings
- **THEN** the "Archive" button SHALL be enabled and the warnings SHALL be displayed for user review
