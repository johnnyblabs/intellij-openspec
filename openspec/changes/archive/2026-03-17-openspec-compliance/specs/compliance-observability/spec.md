## ADDED Requirements

### Requirement: Compliance status computation
The plugin SHALL provide a `ComplianceService` (project-level service) that computes a `ComplianceResult` for a given change. The result SHALL aggregate three categories: (a) artifact completeness, (b) validation results with zero errors, and (c) spec-sync readiness (delta specs target valid capabilities). Each category SHALL report pass/fail independently with actionable remediation messages.

#### Scenario: All categories pass
- **WHEN** `ComplianceService.checkCompliance(change)` is called for a change with complete artifacts, zero validation errors, and valid delta spec targets
- **THEN** the result SHALL report COMPLIANT with all three categories passing

#### Scenario: Validation errors present
- **WHEN** a change has spec files with validation errors (e.g., missing RFC 2119 keywords)
- **THEN** the compliance result SHALL report NOT_COMPLIANT with the validation category failing and remediation messages listing each validation error

#### Scenario: Incomplete artifacts
- **WHEN** a change has scaffolded or missing artifacts
- **THEN** the compliance result SHALL report NOT_COMPLIANT with the artifact completeness category failing and remediation messages identifying which artifacts need content

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
