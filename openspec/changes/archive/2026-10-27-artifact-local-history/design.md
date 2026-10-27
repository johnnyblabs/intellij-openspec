# Design

## Context

See proposal.md for why.

Direct API writes go through two paths:
- **Unreviewed writes:** `ArtifactOrchestrationService.writeArtifactResult`. It is called off the EDT by the workflow panel's Generate, by Generate All and Fast-Forward (`runBatch`), and by Continue. It writes with `WriteAction.runAndWait` plus `setBinaryContent`.
- **Accepted Regenerate:** `ReviewedArtifactWriter.write`, one `WriteCommandAction` called on the EDT from `WorkflowActionPanel.reviewAndWrite`. It already re-checks every file's live text against the reviewed text, and writes an open file through its document.

The DONE chip menu is `WorkflowActionPanel.chipContextMenu`. The popover takes one optional `GuidanceLink`, which is "Diff vs current spec" for specs.

Upstream OpenSpec has no artifact history, snapshot or rollback concept, so after a write the IDE's Local History and the VCS are the only on-model path. jetbrains-platform-guru checked the API set below against 2024.2 and 2026.2 (spot-checked on 2025.2).

## Goals / Non-Goals

**Goals:**
- a label before every Direct API write, whose "before" content is what the user had;
- a durable "Show Local History" entry on the chip;
- a reliable "Compare with previous" right after a Regenerate.

**Non-Goals:**
- reverting from the plugin;
- reading Local History contents;
- a VCS link;
- labelling writes the plugin doesn't make.

## Decisions

**D1. One helper does save-then-label.**
- `ArtifactLocalHistory.beforeWrite(project, labelName, paths)`:
  1. saves the document of any existing target file with unsaved changes, on the EDT;
  2. calls `LocalHistory.getInstance().putSystemLabel(project, labelName)`;
  3. returns nothing: a disabled Local History quietly puts no label (`Label.NULL_INSTANCE`), and the write goes ahead.
- **Why save first:** Local History versions only disk content, so without saving, an unsaved edit would be missing from "before".
- **Rejected:**
  - `putUserLabel`: `@ApiStatus.Internal`, which fails verifyPlugin;
  - `startAction` with `ActivityId`: experimental;
  - `LocalHistory.isEnabled()`: absent on 242.
- **Test seam:** the platform call sits behind a package-private `Labeler` interface with a default instance. Tests can then record the order of save, label and write without a real Local History. Light fixtures use `temp://`, which Local History doesn't track.

**D2. Where the label goes.**
- **Unreviewed writes:** `writeArtifactResult` calls `beforeWrite` with "OpenSpec: before generating `<id>`" after planning the writes and before the first `writeFile`. It already runs off the EDT; `beforeWrite` hops to the EDT only to save documents.
- **Accepted Regenerate:** `ReviewedArtifactWriter.write` calls it with "OpenSpec: before regenerating `<id>`" on the EDT, after the changed-since-review refresh and before `runWriteCommandAction`.
  - The changed-since-review check stays inside the command. Saving a document whose text equals the reviewed text doesn't trip it, and a document that differs fails the check anyway, so nothing is written.
  - So a label can precede a refused write. That is harmless, because it marks unchanged content. Rejecting the review never reaches `write`, so no label is put.
- **Command name:** the `WriteCommandAction` keeps "Regenerate `<id>`", so Local History groups the files under one change.

**D3. "Show Local History" runs the IDE's own action.**
- **How:** `ActionManager.getAction("LocalHistory.ShowHistory")`, null-checked, run through `ActionLauncher.launch`.
- **Why by ID:** there is no public API to open the history view. `FileHistoryDialog`, `ShowHistoryAction` and `LocalHistoryImpl` are impl classes. A string ID gives the verifier nothing to flag.
- **Data context:** the action reads the files from the data context. The launch source is a component implementing `UiDataProvider` (not the obsolete `DataProvider`). Only for that launch, it sets the project, `CommonDataKeys.VIRTUAL_FILE_ARRAY` and the VCS file list. On 2024.2 the action's `getFiles` reads only the VCS file list. That key is created by its name, `VCS_VIRTUAL_FILES`, which is the same on 2024.2 and 2026.2, rather than through `VcsDataKeys`, because the plugin doesn't depend on the VCS module.
- **Target:** the artifact's file, or the change's `specs/` folder for `specs`.
- **Shown only when:** the target exists, `LocalHistory.isUnderControl(file)` is true, and the action exists.

**D4. "Compare with previous" diffs what was reviewed.**
- **What it compares:** after `ReviewedArtifactWriter.write` succeeds, the accepted `PlannedWrite`s already carry each file's reviewed text (`currentText`). The popover link diffs that text against the written file, using the diff APIs `DeltaSpecDiffAction` already uses: `DiffContentFactory`, `SimpleDiffRequest` and `DiffManager`. Several files open as a `SimpleDiffRequestChain`. New files (no reviewed text) are left out.
- **Why not read Local History:** `getByteContent` is `@RequiresBackgroundThread` on 262, and it matches by timestamp.
- **Lifetime:** the reviewed text lives only in the link's closure. It is gone when the popover closes, after its existing 8 s auto-dismiss or a click outside.
- **Popover:** `GuidanceLink` becomes a list, so specs can show both links.

## Risks / Trade-offs

- [Putting the label throws, for example with no Local History for the project] → The label is best-effort: the failure is logged and the write goes ahead, because a label is a convenience beside the write, never a condition for it. Cancellation is still passed on.
- [Local History is disabled (`-Dlvcs.disable.local.history`) or purged (default retention 5 days)] → `putSystemLabel` returns `NULL_INSTANCE` and writes go ahead unchanged. "Show Local History" is hidden when the file isn't tracked. An old label may simply be gone; that is the IDE's retention policy.
- [Saving unsaved edits is a side effect] → It only saves files that are about to be overwritten in the same action, so nothing extra ends up on disk.
- [The Show History view differs between 242 and 262] → The plugin only opens it; nothing depends on its UI.
- [New `com.intellij.*` references] → The pre-push verifyPlugin gate runs. Watch for internal-API or obsolete-API findings.
