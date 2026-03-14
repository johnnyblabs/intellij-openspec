## 1. Fix state detection for projects with archives

- [x] 1.1 Update `GettingStartedPanel.detectState()` to return `READY` when `changesDir/archive/` has children

## 2. Fix wizard propose button

- [x] 2.1 Replace direct `ProposeChangeDialog.show()` in wizard done step with `ActionManager.getAction("OpenSpec.Propose")` invocation

## 3. Verify

- [x] 3.1 Build plugin and confirm zero compilation errors
