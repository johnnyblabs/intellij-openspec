# Tasks

## 1. Fixes

- [x] 1.1 Fence the noted Regenerate's current version with one more backtick than its longest run (at least three). Verify with tests: content with a ```` ``` ```` code block, and content with a four-backtick run, each come back intact between the fences. Content with no backticks keeps the three-backtick fence.
- [x] 1.2 Make `ArtifactLocalHistory.beforeWrite` hop to the EDT only when a target file has an unsaved document. Verify with tests: off the EDT with no unsaved target, the label is put and no EDT hop happens; with an unsaved target, it is saved before the label.
- [x] 1.3 Use find-without-refresh in `localHistoryTarget` and `compareRequests`. Verify with the existing Local History tests, and with a test that the menu still offers Show Local History for a file the VFS already knows.
- [x] 1.4 Add an `onFinished` callback to `UpdatePreviewService.previewAndApply`, called on every outcome. Give Manage AI Tools' Update button the busy state until it fires. Verify with tests:
  - `onFinished` fires once each for applied, cancelled, failed and nothing to do;
  - the button is disabled and reads "..." while the preview is pending;
  - the button is enabled again after a cancel and after a failure.
- [x] 1.5 Register every `@Service(PROJECT)` class in `plugin.xml`. Verify with a source-scan test that fails when any one is missing.

## 2. Docs and checks

- [x] 2.1 No CHANGELOG entries: every defect is in an unreleased feature, so users never saw it. Verify the Unreleased entries for those features still describe the fixed behaviour, and run the leak grep.
- [x] 2.2 Run `./gradlew build` (exit 0) and `openspec validate review-fixes-v0150 --strict`.

## Workflow follow-up

- Archive the change, then close the trackers.
