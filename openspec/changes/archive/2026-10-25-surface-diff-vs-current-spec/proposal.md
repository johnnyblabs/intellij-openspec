# Proposal

## Why

After generating or revising a change's specs, the natural next question is "what does this change do to my specs?" The plugin already answers it with **Preview Diff**, the IDE diff of a delta spec against the current main spec, which is the on-model equivalent of `openspec show --diff`. But it's only offered while browsing: on a delta spec's right-click menu, and as a link in the Change Deltas preview. The workflow panel, where generation happens, never offers it. Tracked by the linked tracker entry.

## What Changes

- **The specs chip offers the diff.** When the specs artifact is done and the change has delta specs, right-clicking its chip in the workflow panel shows **Diff vs current spec**, with one entry per capability. Each entry opens the same diff as Preview Diff.
- **The popover after generating specs links it.** When Direct API generates or regenerates the specs artifact, the confirmation popover adds a **Diff vs current spec** link. With one capability it opens that diff; with several it asks which.
- **The wording never suggests history.** The entries say what the change does compared with the current spec, not what the last generation changed. Before/after review of a regeneration is tracked separately.
- **A new capability is unchanged.** With no main spec yet, the diff shows the delta against an empty "New capability — no existing spec" side, as it does today.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `pipeline-interaction`: adds "Diff vs current spec from the specs chip".
- `guidance-popover`: adds "Diff link after generating specs".

## Impact

- **Code:**
  - `WorkflowActionPanel`: the DONE chip menu, and the popover after a Direct API specs generation;
  - a small menu-model builder for the entries;
  - `DeltaSpecDiffAction` is reused unchanged.
- **Tests:**
  - the builder: one entry per capability, none without delta specs or for other artifacts;
  - the chip menu's contents;
  - the popover link;
  - the wording.
- **Docs:** the feature reference and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported; no new platform APIs.
- **Out of scope:** before/after review of a regeneration, and nested capability folders, which the diff and the tree don't list today.
