# Tasks: fix-cli-detect-button

## 1. Fix detection error handling

- [x] 1.1 In `OpenSpecSettingsPanel.detectCli()`, wrap `detection.detect()` in try/catch on the pooled thread
- [x] 1.2 In the catch block, schedule `invokeLater` to set status label to error message (e.g., "Detection failed: [message]") with error color
- [x] 1.3 Add `LOG.warn()` in the catch block so detection failures appear in idea.log without debug logging

## 2. Ensure UI always repaints

- [x] 2.1 After `cliPathField.setText()` in the success path, call `cliPathField.repaint()`
- [x] 2.2 After `updateCliStatus()`, call `cliStatusLabel.repaint()` to force visual refresh

## 3. Verify

- [x] 3.1 Run `./gradlew clean build test` — all green
