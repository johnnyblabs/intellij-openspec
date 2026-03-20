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
