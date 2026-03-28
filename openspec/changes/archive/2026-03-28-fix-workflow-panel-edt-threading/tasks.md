## 1. Fix EDT violation

- [x] 1.1 In `WorkflowActionPanel.setActiveChange()`, replace `refreshForChangeOnPool(changeName)` with `refreshForChange(changeName)` to dispatch the CLI/orchestration work to a background thread

## 2. Test

- [x] 2.1 Add a test in `WorkflowActionPanelTest` verifying that `setActiveChange()` calls `refreshForChange()` (the background-dispatching method) rather than `refreshForChangeOnPool()` directly

## 3. Verification

- [x] 3.1 Build compiles and all existing tests pass
