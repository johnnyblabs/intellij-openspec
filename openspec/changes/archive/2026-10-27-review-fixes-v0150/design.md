# Design

## Context

See proposal.md. The defects are independent, so each gets its own fix and test. They ship together because each is small and all came from the same review.

## Decisions

**D1. The fence grows with the content.**
- **How:** the fence is a run of backticks one longer than the longest run in the current text, and at least three. That is the CommonMark rule, so a fence of N backticks is closed only by a run of N or more.
- **Rejected:**
  - tildes: an artifact can contain them too;
  - indenting the content: it changes the text the model is asked to revise.

**D2. Save on the UI thread only when something needs saving.**
- **How:** `ArtifactLocalHistory.beforeWrite` first checks, under a read action on the calling thread, whether any target file has an unsaved document. It hops to the EDT (`invokeAndWait`) only then.
- **Why:** the hop is what waited on modal dialogs. Generate All, Fast-Forward and Continue write new files, so they never need it.
- **Rejected:**
  - running Generate All as a `Task.Backgroundable`: a task started from the non-modal tool window posts with the same non-modal state, so a dialog opened mid-run would still block it;
  - `ModalityState.any()`: it would save documents under someone else's modal dialog.
- **Out of scope:** the write itself (`WriteAction.runAndWait`) still waits on an open modal, as it did before this release. That is tracked with the other review follow-ups.

**D3. Find, don't refresh, on the EDT.**
- **How:** `localHistoryTarget` and `compareRequests` use `findFileByPath` and `findFileByNioFile`.
- **Why it's safe:** the targets are a done artifact the panel already listed, and files the plugin itself just wrote through the VFS.
- **Unchanged:** `ReviewedArtifactWriter.write` keeps its refresh, deliberately: the changed-since-review check needs edits made outside the IDE.

**D4. The busy state comes from `runCliAction`; the preview says when it's finished.**
- **How:** the Update button is disabled and reads "..." before the dry run starts.
- **The gap:** `UpdatePreviewService.previewAndApply` calls back only after Apply runs. A failed dry run, an empty plan and Cancel end without a callback, so the button would stay busy.
- **Fix:** the service gains an `onFinished` callback, called on the UI thread on every outcome (applied, cancelled, failed, nothing to do). The dialog restores the button there: `loadTools()` rebuilds the row after an apply; otherwise the button is re-enabled, reading "Retry" after a failure, as `runCliAction` does.

**D5. Register every project service, and test it.**
- **How:** add `<projectService>` entries for `UpdatePreviewService`, `UpdateLegacyCleanupService` and `WorkflowSchemaContextService`.
- **The test:** a source scan asserts every `@Service(Service.Level.PROJECT)` class under `src/main/java` has an entry. Resolving the service in a test would prove nothing, because an annotated light service resolves without one.

## Risks / Trade-offs

- [The unsaved-document check runs off the EDT] → It reads `FileDocumentManager.getCachedDocument` and `isDocumentUnsaved` under a read action. A document edited between the check and the write is caught by the write paths: Regenerate's changed-since-review check, and the plain writes of new files.
