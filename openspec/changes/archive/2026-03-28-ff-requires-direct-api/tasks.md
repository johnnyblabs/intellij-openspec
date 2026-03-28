## 1. FF Action — Direct API Gate

- [x] 1.1 Override `update()` in `OpenSpecFfAction` to check `DirectApiService.isConfigured()` after the base class profile check; disable with description tooltip when not configured
- [x] 1.2 Add test verifying FF action is disabled when Direct API is not configured and enabled when it is

## 2. WorkflowActionPanel — Conditional FF Link

- [x] 2.1 In the "no changes" card builder, conditionally include the FF link and "or" label only when `DirectApiService.isConfigured()` is true
- [x] 2.2 Guard `activateFfInput()` to show an inline message instead of the FF form when Direct API is not configured
- [x] 2.3 Add test verifying FF link visibility tracks Direct API configuration state

## 3. Verification

- [x] 3.1 Run existing `WorkflowActionPanelTest` to confirm no regressions
- [x] 3.2 Run full test suite to confirm no side effects