# Tasks: zero-state-welcome

## 1. Implement zero state

- [x] 1.1 Change `shouldBeAvailable()` in `OpenSpecToolWindowFactory` to always return true
- [x] 1.2 In `createToolWindowContent()`, check `isOpenSpecProject()` and show either a welcome panel or the normal browse/console panels
- [x] 1.3 Create a welcome panel with "Initialize OpenSpec" button, brief explanation, and plugin branding
- [x] 1.4 After Init, refresh the tool window to show the normal view

## 2. Verify

- [x] 2.1 Run `./gradlew clean build test` — all green
