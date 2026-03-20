## 1. Validation Strictness (foundation — other components depend on error-level results)

- [x] 1.1 In `BuiltInValidator`, promote RFC 2119 keyword check from WARNING to ERROR: a `### Requirement:` block without at least one SHALL/MUST/SHOULD/MAY produces an ERROR `ValidationResult`
- [x] 1.2 In `BuiltInValidator`, promote scenario clause check from WARNING to ERROR: a `#### Scenario:` block missing WHEN or THEN produces an ERROR `ValidationResult`
- [x] 1.3 In `BuiltInValidator`, add requirement-must-have-scenario rule: a `### Requirement:` block with no `#### Scenario:` blocks produces an ERROR
- [x] 1.4 In `BuiltInValidator`, add delta spec structural validation for ADDED/MODIFIED sections: each `### Requirement:` block must have a description and at least one `#### Scenario:` with WHEN/THEN — violations produce ERRORs
- [x] 1.5 In `BuiltInValidator`, add delta spec structural validation for REMOVED sections: each `### Requirement:` block must contain `**Reason**` and `**Migration**` fields — missing fields produce ERRORs
- [x] 1.6 Update `DeltaSpecInspection` to use the new structural validation rules from `BuiltInValidator` instead of its current presence-only check
- [x] 1.7 Update existing validation tests to expect ERROR severity for RFC 2119 and scenario clause violations
- [x] 1.8 Add tests for delta spec structural validation (ADDED/MODIFIED missing scenarios, REMOVED missing Reason/Migration, valid delta passes)
- [x] 1.9 Add tests for requirement-must-have-scenario rule (requirement without scenario → ERROR, requirement with scenario → no error)

## 2. ComplianceService & Registration

- [x] 2.1 Register `OpenSpec.Compliance` notification group in `plugin.xml` with `displayType="STICKY_BALLOON"`
- [x] 2.2 Create `ComplianceResult` model class with per-category (artifact completeness, validation, sync readiness) pass/fail status, severity (ERROR/WARNING), and remediation messages
- [x] 2.3 Create `ComplianceService` as `@Service(Service.Level.PROJECT)`, register in `plugin.xml`
- [x] 2.4 Implement `ComplianceService.checkCompliance(Change)`: delegate to `VerificationService` for artifact completeness, `BuiltInValidator` for validation, `SpecSyncService` for delta spec readiness — aggregate into `ComplianceResult`
- [x] 2.5 Add tests for `ComplianceService`: all-pass, validation errors, missing artifacts, sync readiness check

## 3. Archive Pre-Flight Compliance Gate

- [x] 3.1 Create `CompliancePreFlightDialog`: modal dialog showing per-category compliance results with pass/fail icons, finding details, and remediation text — Archive button disabled when ERRORs exist, enabled for WARNING-only
- [x] 3.2 Update `OpenSpecArchiveAction` to call `ComplianceService.checkCompliance()` and show `CompliancePreFlightDialog` before performing the archive — block if ERRORs, allow if WARNINGs only
- [x] 3.3 Add tests for pre-flight gate: ERROR blocks archive, WARNING allows archive, clean compliance proceeds

## 4. Spec-Sync Post-Merge Validation

- [x] 4.1 In `SpecSyncService.applySync()`, after writing merged content, run `BuiltInValidator.validateSpecFile()` on each affected main spec
- [x] 4.2 Compare pre-merge and post-merge validation results to distinguish pre-existing issues from sync-introduced issues — report only new issues
- [x] 4.3 In strict mode, make MODIFIED operations targeting nonexistent requirements produce an ERROR that blocks the sync for that capability
- [x] 4.4 Add tests for post-merge validation: clean merge (no warnings), merge introduces issues (reported), pre-existing issues not reported, strict-mode unmatched MODIFIED blocks sync

## 5. Compliance Observability UI

- [x] 5.1 Add compliance status chip to `WorkflowActionPanel`: colored label showing "Compliant" (green), "N issues" (yellow), or "Not checked" (gray) — positioned next to the artifact pipeline chips
- [x] 5.2 Wire chip click to run `ComplianceService.checkCompliance()` and open `CompliancePreFlightDialog` with results
- [x] 5.3 Update chip state when the selected change switches in the change selector dropdown (reset to "Not checked")
- [x] 5.4 Add compliance notification helper to `OpenSpecNotifier`: method to post sticky compliance notifications using the `OpenSpec.Compliance` group

## 6. Integration Verification

- [x] 6.1 End-to-end test: propose a change with valid specs → compliance check passes → archive succeeds
- [x] 6.2 End-to-end test: propose a change with invalid specs (missing scenarios) → compliance check fails → archive blocked with remediation dialog
- [x] 6.3 Verify existing plugin tests still pass after validation severity changes (update test fixtures that relied on WARNING severity)
