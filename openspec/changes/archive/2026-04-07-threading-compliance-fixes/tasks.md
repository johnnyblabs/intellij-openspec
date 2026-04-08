## 1. CliRunner — Replace OSProcessHandler with Process

- [x] 1.1 Remove `OSProcessHandler`, `ProcessListener`, `ProcessOutputTypes`, and `Key` imports from `CliRunner.java`
- [x] 1.2 Replace `OSProcessHandler`-based execution with `GeneralCommandLine.createProcess()` + `Process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)`
- [x] 1.3 Add `drainStreamAsync()` helper using `CompletableFuture.supplyAsync()` to read stdout and stderr concurrently
- [x] 1.4 Return `CliResult` using `process.exitValue()` and joined futures

## 2. CliDetectionService — Replace OSProcessHandler with Process

- [x] 2.1 Remove `OSProcessHandler`, `ProcessListener`, `ProcessOutputTypes`, and `Key` imports from `CliDetectionService.java`
- [x] 2.2 Extract `runAndCapture(GeneralCommandLine)` private method using `Process` directly with `readAllBytes()` before `waitFor()`
- [x] 2.3 Replace inline OSProcessHandler logic in `tryPath()` with `runAndCapture()` call
- [x] 2.4 Replace inline OSProcessHandler logic in `tryLoginShellWhich()` with `runAndCapture()` call
- [x] 2.5 Replace inline OSProcessHandler logic in `resolveLoginShellPath()` with `runAndCapture()` call

## 3. SpecSyncService — Separate file write from WriteAction

- [x] 3.1 Move `Files.writeString()` call outside of `invokeAndWait`/`WriteAction` block in `applySync()`
- [x] 3.2 Keep only `LocalFileSystem.refreshAndFindFileByPath()` and `VirtualFile.refresh()` inside `WriteAction`

## 4. WorkflowActionPanel — Thread safety fixes

- [x] 4.1 Add `volatile` to `activeChangeName`, `nextArtifactId`, `lastPrompt`, `lastOutputPath`, and `hasDeltaSpecs` fields
- [x] 4.2 Capture `getSelectedDeliveryMode()` and `getSelectedToolName()` on EDT before `executeOnPooledThread` in `onApplyTasks()`

## 5. Verification

- [ ] 5.1 Build the plugin (`./gradlew buildPlugin`) and confirm no compile errors
- [ ] 5.2 Run in IDE sandbox and trigger action update cycle — confirm no `Synchronous execution under ReadAction` throwable in idea.log
- [ ] 5.3 Run Sync Specs on a change with delta specs — confirm file writes succeed and VFS updates correctly
