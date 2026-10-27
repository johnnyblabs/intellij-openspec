# Proposal

## Why

Regenerate already shows a review before it writes, and the write can be undone. But once you've moved on, the plugin gives you no way to see what a past generation changed or to get the earlier text back: Undo only reaches back while the editor's undo stack holds it. Upstream OpenSpec has no artifact history, so the IDE's Local History, along with your VCS, is the on-model place for "what changed, and how do I get it back". The plugin can mark each generation there and make it one click away, without storing anything itself. Tracked by the linked tracker entry.

## What Changes

- **Every Direct API write is marked in Local History.** Before Generate, Generate All Remaining, Fast-Forward, Continue or an accepted Regenerate writes an artifact, the plugin puts a Local History label: **"OpenSpec: before generating `<artifact>`"** or **"OpenSpec: before regenerating `<artifact>`"**. Unsaved edits in the files about to be written are saved first, so the label's "before" is what you saw, not an older copy on disk.
- **"Show Local History" on a DONE chip.** The right-click menu opens the IDE's own Local History for the artifact's file. For `specs`, it opens the change's `specs/` folder. The item is left out when the file doesn't exist, when Local History doesn't track it, or when the IDE has no Local History view.
- **"Compare with previous" after a Regenerate.** When an accepted Regenerate changed at least one existing file, its confirmation popover links a diff of the text you reviewed against what was written, one file after another for a multi-file artifact. The previous text is held only for that diff and never saved. For `specs`, the popover keeps its "Diff vs current spec" link as well.
- **Nothing is stored by the plugin.** There is no history, snapshot or undo stack in `openspec/`, settings or any sidecar. The wording says "Local History", never "version".

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: adds requirements for the Local History label before Direct API writes and for "Compare with previous" after a Regenerate.
- `pipeline-interaction`: adds the "Show Local History" item to the DONE chip menu.

## Impact

- **Code:**
  - `ArtifactOrchestrationService.writeArtifactResult` and `ReviewedArtifactWriter.write`: save the targets, then label, before writing;
  - `WorkflowActionPanel`: the chip menu item, and the popover's second link;
  - a small labeler seam for tests.
- **Platform APIs (all public and non-deprecated on 2024.2 to 2026.2):**
  - `LocalHistory.putSystemLabel` and `isUnderControl`;
  - the IDE's `LocalHistory.ShowHistory` action, called by its ID;
  - `UiDataProvider`;
  - the diff APIs the plugin already uses.

  The pre-push verifyPlugin gate applies.
- **Tests:**
  - the call order (save, label, write);
  - a disabled Local History hides the links;
  - menu item visibility;
  - the compare diff's content.
- **Docs:** the feature reference and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported.
- **Out of scope:**
  - a VCS history link;
  - reverting from the plugin (Local History's own Revert does that);
  - labels for writes that don't go through Direct API.
