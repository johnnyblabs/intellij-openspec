# Compliance

## Purpose
Pre-archive compliance checking across three categories — artifact completeness, validation, and sync readiness — with a gated dialog that prevents archiving non-compliant changes.

## Requirements

### Requirement: Three-category compliance check

The plugin SHALL evaluate a change against three compliance categories: artifact completeness, validation, and sync readiness. Each category SHALL independently produce errors and warnings.

#### Scenario: Artifact completeness check
- **WHEN** compliance is evaluated for a change
- **THEN** the service SHALL verify all required artifacts exist with real content, mapping critical findings to errors and warnings to warnings under the ARTIFACT_COMPLETENESS category

#### Scenario: Validation check
- **WHEN** compliance is evaluated for a change
- **THEN** the service SHALL run built-in validation on the change's artifacts, mapping ERROR severity findings to errors and WARNING severity findings to warnings under the VALIDATION category

#### Scenario: Sync readiness check
- **WHEN** compliance is evaluated for a change that has delta specs
- **THEN** the service SHALL verify that MODIFIED operations target existing capabilities with main spec files, reporting warnings under the SYNC_READINESS category for any unmatched targets

#### Scenario: No delta specs skips sync check
- **WHEN** a change has no delta spec sections
- **THEN** the sync readiness check SHALL be skipped with no findings

### Requirement: Compliance pre-flight dialog

The plugin SHALL display a modal dialog showing compliance results before allowing archive, with category-level pass/fail indicators and individual findings.

#### Scenario: All categories pass with no warnings
- **WHEN** compliance check returns no errors and no warnings
- **THEN** the dialog SHALL show a green status header, all categories with pass indicators, and the Archive button SHALL be enabled

#### Scenario: Compliant with warnings
- **WHEN** compliance check returns no errors but has warnings
- **THEN** the dialog SHALL show an orange status header, affected categories with their warnings listed, and the Archive button SHALL be enabled

#### Scenario: Non-compliant with errors
- **WHEN** compliance check returns one or more errors
- **THEN** the dialog SHALL show a red status header, affected categories with their errors listed, and the Archive button SHALL be disabled

#### Scenario: Finding display
- **WHEN** findings are displayed in the dialog
- **THEN** each finding SHALL show its severity (ERROR or WARNING) with color coding and a descriptive message

### Requirement: Compliance notification group

The plugin SHALL register an `OpenSpec.Compliance` notification group with display type STICKY_BALLOON. All compliance-related notifications (pre-flight results, compliance check outcomes) SHALL use this group.

#### Scenario: Compliance notification registration
- **WHEN** the plugin starts
- **THEN** `OpenSpec.Compliance` SHALL be registered in `plugin.xml` as a notification group with STICKY_BALLOON display type

#### Scenario: Compliance failure notification
- **WHEN** a compliance check finds errors
- **THEN** the plugin SHALL display a sticky balloon notification listing each failing category and the first remediation action for each

### Requirement: Compliance status chip in workflow panel

The plugin SHALL display a compliance status indicator in the `WorkflowActionPanel` next to the artifact pipeline chips. The chip SHALL show one of three states: "Compliant" (green), "Issues found" (yellow, with count), or "Not checked" (gray). Clicking the chip SHALL run `ComplianceService.checkCompliance()` and display the full result in a dialog.

#### Scenario: Compliant change
- **WHEN** the selected change passes all compliance categories
- **THEN** the chip SHALL display green with the text "Compliant"

#### Scenario: Non-compliant change
- **WHEN** the selected change has compliance issues
- **THEN** the chip SHALL display yellow with "N issues" where N is the total count of errors and warnings

#### Scenario: Not yet checked
- **WHEN** no compliance check has been run for the selected change in the current session
- **THEN** the chip SHALL display gray with "Not checked"

#### Scenario: Click to check
- **WHEN** the user clicks the compliance chip
- **THEN** the plugin SHALL run the compliance check and display a dialog with per-category results and remediation guidance

### Requirement: Compliance result dialog

The plugin SHALL display compliance check results in a modal dialog with one section per category (artifact completeness, validation, spec-sync readiness). Each section SHALL show pass/fail status, the specific findings, and actionable remediation text for failures.

#### Scenario: Dialog structure
- **WHEN** the compliance result dialog opens
- **THEN** it SHALL display three collapsible sections, one per compliance category, each showing a pass/fail icon and finding details

#### Scenario: Remediation guidance
- **WHEN** a category shows a failure
- **THEN** the section SHALL include specific remediation text (e.g., "Requirement 'X' has no scenarios — add at least one `#### Scenario:` block")
