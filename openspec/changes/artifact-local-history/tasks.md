# Tasks

## 1. Label before writes

- [x] 1.1 Add `ArtifactLocalHistory.beforeWrite(project, labelName, paths)` with a package-private `Labeler` seam: save the unsaved documents of existing targets (on the EDT), then put the system label. Verify with a test, on and off the EDT, that a dirty target document is saved before the label and a clean or missing file isn't touched.
- [x] 1.2 Call it from `ArtifactOrchestrationService.writeArtifactResult` ("OpenSpec: before generating `<id>`") before the first file is written. Verify with a test through the seam that Generate records the label before the file exists or changes, and that a write that fails to plan puts no label.
- [x] 1.3 Call it from `ReviewedArtifactWriter.write` ("OpenSpec: before regenerating `<id>`") before the write command. Verify with tests: the label comes before the write, a rejected review puts no label, and an unsaved edit equal to the reviewed text is saved and then written.

## 2. Show Local History

- [x] 2.1 Add "Show Local History" to the DONE chip menu, after "Copy prompt". It runs `LocalHistory.ShowHistory` through `ActionLauncher` with a `UiDataProvider` source that supplies the project and the file (or the `specs/` folder) as `VIRTUAL_FILE_ARRAY` and as the VCS file list (`VCS_VIRTUAL_FILES`, by key name). Hide it when the target is missing, untracked, or the action is absent. Verify with menu tests:
  - present for a tracked file;
  - absent for a missing file, an untracked file or a missing action;
  - the specs chip targets the `specs/` folder;
  - the launch source's data snapshot carries the project and the target under both keys;
  - the real action ID is registered in the test IDE.

## 3. Compare with previous

- [x] 3.1 Make the popover accept several links. After an accepted Regenerate, add "Compare with previous" when at least one written file existed before. It opens a diff, or a chain for several files, of the reviewed text against the written file. Verify with tests:
  - the link's diff requests carry the reviewed text and the written text;
  - new files are left out;
  - an all-new write has no link;
  - specs show both links;
  - the popover's other links are unchanged.

## 4. Docs and checks

- [ ] 4.1 Update `docs/feature-reference.md` (workflow panel chip menu, Direct API generation and Regenerate) and add a CHANGELOG Unreleased → Added entry. Verify with the leak grep and `DocumentationHygieneTest`.
- [ ] 4.2 Run `./gradlew build` (exit 0) and `./gradlew verifyPlugin` (no internal, experimental or scheduled-for-removal API usages; grep the report for "will be removed"), plus `openspec validate artifact-local-history --strict`.

## Workflow follow-up

- Try it in the sandbox IDE: regenerate an artifact, then choose Show Local History on its chip, check the label is there, and use Compare with previous from the popover.
- Archive the change, then close the trackers.
