# Design

## Context

- **Regenerate today:**
  - `WorkflowActionPanel.onRegenerateArtifact` shows a yes/no dialog only when the artifact has completed downstream artifacts, then calls `executeGeneration` with the selected delivery mode.
  - The prompt is `ArtifactInstruction.buildPrompt()` for clipboard and editor tab, and `buildDirectApiPrompt()` for Direct API, which adds the multi-file format and existing files for a glob artifact.
- **Direct API writes today:** `executeGeneration` runs a `Task.Backgroundable` that calls the API and then `ArtifactOrchestrationService.writeArtifactResult` off the EDT.
  - For a glob artifact (specs) the response is parsed with `MultiFileArtifact.parse` and every file is written, or none if it is refused.
  - Generate All Remaining and Fast-Forward use the same writer.
- **Upstream (CLI 1.15.0):**
  - `openspec instructions` has no steering or note input.
  - `continue-change` only creates the next artifact; it never redoes a done one.
  - `update-change` drafts an edit in the conversation and writes only after the user confirms.
  - So Regenerate is plugin-only, and a note can only be plugin-side prompt text. The review-before-write gate brings Direct API Regenerate in line with upstream's confirm-before-write.
- **A pattern to follow:** `ReviseChangeDialog` asks "What should change?" in a `DialogWrapper` with a note area.

## Goals / Non-Goals

**Goals:**

- Say what should change when regenerating, in any delivery mode.
- Never overwrite an artifact over Direct API without the user seeing and accepting the result.

**Non-Goals:**

- Writing or regenerating part of a file.
- Pre-filling the note from an editor selection.
- A note on Copy prompt; revising a whole change stays with Revise Change....
- Editing the generated text inside the review. A later change could make the generated side editable.
- Any stored history of generations or notes.

## Decisions

### D1. One Regenerate dialog
- **The dialog:** `RegenerateArtifactDialog`, a `DialogWrapper` like `ReviseChangeDialog`, titled "Regenerate <artifact>".
  - It has a multi-line "What should change?" note, optional.
  - When `getCompletedDownstream` is non-empty, it adds a warning line naming those artifacts. This replaces the separate yes/no dialog, so there is one confirmation.
  - OK is labelled "Regenerate".
- **The note keeps its line breaks:** unlike Revise's one-line handoff, it is prompt text, so it is only stripped.
- **Testing:** a dialog factory seam on the panel, like the topic-dialog seam, lets tests supply the note and the OK or Cancel answer.

### D2. The note in the prompt
- **The overload:** `ArtifactInstruction.buildPrompt(@Nullable String note)`. A null or blank note returns `buildPrompt()` unchanged, byte for byte.
- **The section:** otherwise one trailing section follows the dependencies:

  ```
  ---

  Note for this regeneration (follow the instructions above; this note only says what to change):
  <note>
  ```

- **Direct API gets the same:** `buildDirectApiPrompt(@Nullable String note)`, with the note placed before the multi-file format block, which must stay last.
- **The current file:** with a non-blank note and a single-file output path, the Direct API prompt also includes the artifact's current content under "Current version (revise this)". It is read the way dependencies are read. Glob artifacts already include their existing files. Clipboard and editor-tab prompts don't need it, since an agent reads the file itself.
- **The note is passed down, never stored:**
  - it travels as a parameter from the dialog through `executeGeneration`;
  - it is never kept in a field that outlives the run;
  - it is never written to settings, `.openspec.yaml` or `config.yaml`.
  - editor-tab delivery opens the prompt in an unsaved `LightVirtualFile` tab, as Explore and Revise do, instead of writing it to a temporary file.

### D3. Plan, then write
- **Split `writeArtifactResult`:**
  - `planArtifactResult(instruction, content)` is pure and off the EDT. It applies today's parse and refusal rules and returns `PlannedWrite(path, currentText or null, newText)` for each file.
  - `applyPlannedWrites(project, label, writes)` writes them.
  - `writeArtifactResult` becomes plan plus apply, so Generate All and Fast-Forward are unchanged.
- **A refusal stays as it is:** an invalid path or no marked files fails in planning, before any review, with today's message.

### D4. The review
- **When it opens:** only for Direct API Regenerate. Generate from a ready chip, Generate All and Fast-Forward write without one.
- **How the run reaches it:**
  - The background task generates and plans in `run()`.
  - Its `onSuccess()`, on the EDT and skipped on cancel, passes the plan to an `ArtifactReviewer`.
  - The reviewer returns the writes to apply, or null for Reject.
  - The dialog is never awaited inside `run()`.
- **The dialog:** `ArtifactReviewDialog`, a modal `DialogWrapper` with OK "Accept" and Cancel "Reject". All of its APIs exist on 2024.2 and are neither experimental nor deprecated through 2026.2.
  - On the left, a `JBList` of the planned files, with new files marked "(new)".
  - On the right, a `DiffRequestPanel` from `DiffManager.createRequestPanel(project, getDisposable(), window)`. Selecting a file sets its `SimpleDiffRequest`.
  - The current file is on the left side, read-only, or `createEmpty()` for a new file. The generated text, read-only and typed by the file name, is on the right.
- **Accept:** one `WriteCommandAction` named "Regenerate <artifact>", so it's undoable. A file open in an editor is written through its `Document`; any other file through the VFS.
  - Before writing, each file's current text is compared with what the review showed. On any difference nothing is written and the user is told the file changed since the review.
- **Reject or close:** nothing is written, and a quiet notice says the regeneration was discarded.
- **Testing:** the reviewer is a seam on the panel, defaulting to the dialog, so headless tests can accept, reject or edit the plan.

## Risks / Trade-offs

- **A modal dialog** interrupts. It's what the user asked for: a decision before the files change.
- **The "changed since the review" check** may refuse after a harmless edit. Regenerating again is cheap, and overwriting an unseen edit isn't.
- **Large multi-file results** mean several files to review, one at a time in the list. That's the honest cost of reviewing a set of files.
- **The current file in the prompt** makes noted regenerations longer. Only a noted single-file regeneration over Direct API pays for it.
