## 1. Fix Text Wrapping in EmptyStateFactory

- [x] 1.1 Wrap description string in `<html><body style='width:280px'>` in `EmptyStateFactory.createPanel()` so text wraps in narrow tool windows

## 2. Post-Propose Transition to Tree View

- [x] 2.1 Add `ToolWindow` field to `GettingStartedPanel` constructor
- [x] 2.2 Update `OpenSpecToolWindowFactory.createGettingStartedContent()` to pass `ToolWindow` to `GettingStartedPanel`
- [x] 2.3 Extract `createNormalContent()` from `OpenSpecToolWindowFactory` into a static/accessible method
- [x] 2.4 In `GettingStartedPanel.createProposeButton()`, after successful propose action, re-detect state and if READY, replace tool window content with Browse + Console tabs

## 3. Verification

- [x] 3.1 Build compiles with no errors
- [x] 3.2 All existing tests pass
- [x] 3.3 Verify description text wraps in a narrow tool window
- [x] 3.4 Verify tool window transitions to tree view after first propose
