# Proposal

## Why

Regenerate is a blind redo: there is no way to say what should change, and over Direct API the result overwrites the artifact with no chance to review it. OpenSpec's own revise workflow drafts an edit first and writes only after you confirm, so a silent overwrite runs against the model the plugin wraps. Users asked to steer a regeneration and to see what it changed before keeping it. Tracked by the linked tracker entry.

## What Changes

- **Regenerate takes an optional note.** Regenerate on a done chip opens one dialog that asks what should change, with a note field that may be left blank. The dialog also carries the existing warning about downstream artifacts, so there is still one confirmation, not two. The note applies in every delivery mode.
- **The note is one-off prompt text.** It is appended to the artifact's prompt as one clearly marked trailing section, subordinate to the schema's instructions. It is never saved: not in settings, `.openspec.yaml`, `config.yaml` or any artifact file. With no note, the prompt is byte-identical to today's.
- **Open in Editor Tab stops writing the prompt to a temporary file.** It opens an unsaved tab, as Explore and Revise Change do, so a note never reaches the disk.
- **Over Direct API, a regeneration with a note sees the current file.** The model is given the artifact's current content so it can revise it rather than start over. Glob artifacts (specs) already include their existing files.
- **Direct API Regenerate asks before it writes.** The generated result opens in a review that compares every affected file with the current one: one file, or every file of a multi-file artifact, including new ones. **Accept** writes them all. **Reject** writes nothing and leaves the files byte-identical.
- **Unchanged:** Copy prompt, Generate on a ready chip, Generate All Remaining and Fast-Forward. Those create artifacts that don't exist yet, so nothing is overwritten. Revising a whole change with an agent stays with Revise Change....

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `pipeline-interaction`: adds "Regenerate with an optional note".
- `ai-integration`: adds "Review before Direct API Regenerate writes".

## Impact

- **Code:**
  - `WorkflowActionPanel`: the Regenerate flow and the Direct API write path;
  - a small regenerate dialog modelled on the Revise Change dialog;
  - `ArtifactInstruction`: a prompt overload that takes the note, the no-note path unchanged;
  - a review surface for the generated files, with Accept/Reject.
- **Tests:**
  - prompt byte-identity with no note, against the captured `instructions` fixtures;
  - the note appears exactly once, after the dependencies;
  - nothing but the artifact is written;
  - Accept writes every file and Reject writes none;
  - the dialog's contents and the downstream warning.
- **Docs:** the feature reference and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported. The review uses only diff APIs available and non-experimental on 2024.2; `verifyPlugin` gates this.
- **Out of scope:**
  - regenerating or writing only part of a file;
  - pre-filling the note from an editor selection;
  - a note on Copy prompt;
  - any stored history of generations.
