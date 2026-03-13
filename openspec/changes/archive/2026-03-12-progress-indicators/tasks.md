## 1. Progress indicators

- [x] 1.1 Wrap `executeGeneration()` in `Task.Backgroundable` with cancellable progress
- [x] 1.2 Wrap `onValidateChange()` in `Task.Backgroundable`
- [x] 1.3 Wrap archive operation in `Task.Backgroundable` with `ProcessCanceledException` handling
- [x] 1.4 Fix missing `generateButton.setEnabled(false)` at start of generation (Junie regression)

## 2. Verify

- [x] 2.1 Run full test suite to confirm no regressions
