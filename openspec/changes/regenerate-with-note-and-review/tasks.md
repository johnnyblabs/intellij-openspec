# Tasks

## 1. The note

- [x] 1.1 Add `buildPrompt(note)` and `buildDirectApiPrompt(note)` (design D2). Verify:
  - a null or blank note is byte-identical to today's prompt for every captured `instructions-*.json` fixture;
  - a note appears exactly once, after the dependencies, under the subordinate heading, and before the multi-file block for a glob artifact;
  - a noted single-file Direct API prompt includes the current file under "Current version (revise this)", and an unnoted one doesn't.
- [x] 1.2 Add `RegenerateArtifactDialog` and route Regenerate through it (design D1). Verify:
  - the downstream warning names the completed downstream artifacts and no second dialog follows;
  - Cancel generates, copies and writes nothing;
  - the note reaches the prompt in clipboard, editor-tab and Direct API delivery;
  - the next dialog opens empty;
  - nothing outside the artifact's output is written;
  - Copy prompt opens no dialog and its prompt is unchanged.

## 2. Review before writing

- [x] 2.1 Split `writeArtifactResult` into plan and apply (design D3). Verify:
  - the plan for a captured specs response matches `MultiFileArtifact.parse`, with current text for existing files and null for new ones;
  - refused responses fail in planning with today's messages;
  - Generate All and Fast-Forward still write through plan plus apply.
- [x] 2.2 Add the `ArtifactReviewer` seam and `ArtifactReviewDialog`, shown from the task's `onSuccess` for Direct API Regenerate only (design D4). Verify:
  - Accept writes every planned file, new files and directories included, as one undoable command;
  - Reject and close leave the files byte-identical and report the discard;
  - a cancelled run never reaches the reviewer;
  - a file changed after planning aborts the write with nothing written;
  - a ready-chip Generate writes without the reviewer.

## 3. Docs and gates

- [x] 3.1 Update the feature reference (Regenerate, the review) and add a CHANGELOG line; update README if it describes Regenerate. Verify: `DocumentationHygieneTest` passes.
- [ ] 3.2 Mutation-check three things, each of which must fail a test and then be reverted:
  - drop the note from the prompt;
  - write on Reject;
  - skip the changed-since-review check.

  Then run `./gradlew build`, `./gradlew verifyPlugin` and the full uiSmoke suite (`caffeinate -dimsu`). Verify: all green, 5/5 IDEs Compatible with no new deprecations, and the existing uiSmoke journeys still pass. A booted IDE can't reach the review without a real API key, so `ArtifactReviewDialogTest` covers the review's read-only sides, file list and empty side instead.
