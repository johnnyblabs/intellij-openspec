## Why

Selecting a change's `specs` node in the tool window leaves the preview blank, showing "Select a change to see its consolidated deltas." even though the user did select something under a change. The node's path is the CLI's output path for the artifact, the glob `specs/**/*.md`. The preview treats that path as a file, finds nothing, and falls back to the empty state.

Tracked in the linked tracker entry.

## What Changes

- **A change's `specs` artifact previews the change's deltas.** Selecting it shows the same consolidated, CLI-sourced deltas view as selecting the change itself, including the per-capability diff links.
- **A glob output path is never looked up as a file.** Any other artifact whose output path is a glob (possible with a custom schema) shows no preview instead of a failed file lookup.
- The wording of the empty state is out of scope; the tracker entry for stating the preview pane's purpose owns it.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `spec-viewer`: adds a requirement that selecting a change's spec-deltas artifact renders that change's consolidated deltas, and that glob output paths are never read as files.

## Impact

- **Code:** `toolwindow/SpecPreviewRenderer.classify` and `toolwindow/OpenSpecToolWindowPanel.snapshotSelection`. No change to the tree, the deltas renderer or the CLI calls.
- **Tests:** `classify` against the real captured `outputPath` of the `specs` artifact, plus a test that the `specs` node's selection resolves to the change's deltas view, keyed by the change name and the change directory.
- **Platforms:** no change to IntelliJ 2024.2+ compatibility; no new platform API.
