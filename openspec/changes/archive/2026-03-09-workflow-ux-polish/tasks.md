# Tasks: workflow-ux-polish

## 1. Auto-focus after Propose

- [x] 1.1 Add `selectChange(String changeName)` method to `WorkflowActionPanel` that sets the active change and refreshes
- [x] 1.2 Update `OpenSpecProposeAction` to call `selectChange()` on the workflow panel after creating a change

## 2. Waiting state on Generate button

- [x] 2.1 After clipboard copy, update the Generate button to show "Waiting for [artifact]..." with disabled state
- [x] 2.2 When file watcher detects the artifact, restore the Generate button to its normal state

## 3. READY chip triggers generation

- [x] 3.1 Add click handler to READY pipeline chips that triggers generation for that specific artifact

## 4. Verify

- [x] 4.1 Run `./gradlew clean build test` — all green
