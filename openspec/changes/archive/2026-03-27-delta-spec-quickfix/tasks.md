## 1. Quick-Fix Implementation

- [x] 1.1 Add inner class `CopyRequirementFromMainSpec implements LocalQuickFix` in `DeltaSpecInspection`
- [x] 1.2 Implement `applyFix()` — resolve main spec via VFS, find matching requirement block, replace in delta spec
- [x] 1.3 Wire the quick-fix into the MODIFIED-missing-scenarios problem descriptor (replace `null` with the fix instance)
- [x] 1.4 Only offer the fix when the matching requirement exists in the main spec

## 2. Testing

- [x] 2.1 Test: MODIFIED requirement missing scenarios with matching main spec offers quick-fix
- [x] 2.2 Test: MODIFIED requirement missing scenarios without matching main spec shows error only (no fix)
- [x] 2.3 Test: quick-fix inserts full requirement block from main spec
