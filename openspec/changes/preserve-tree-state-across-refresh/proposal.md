## Why

The Changes tree in the tool window refreshes whenever a file under `openspec/` changes. That includes autosave, an agent editing files from a terminal, a git checkout, and the plugin's own actions. Every refresh drops the user's selection, so the preview pane goes blank. A change whose task count just moved also collapses. The user has to find their place again after work they didn't start, and it looks like the plugin broke.

Tracked in the linked tracker entry.

## What Changes

- **The selection survives a refresh.** After a rebuild, the node the user had selected is selected again and scrolled into view, matched by what it is (its change, artifact or delta-spec file), not by its label. The preview then re-renders that node with the refreshed content. It does not blank in between.
- **Expansion survives label changes.** Expanded nodes are matched by the same identity, so a change stays expanded when its `X/Y` task count or an artifact's `(needs: …)` suffix changes.
- **A node that is gone is handled explicitly.** If the selected node no longer exists (the change was archived or renamed, or the artifact was removed), the selection clears. The workflow panel is told too, so it doesn't keep showing a change the tree no longer has.
- **A failed rebuild keeps the current tree, silently.** Such failures are transient races, such as a directory removed mid-scan or a file caught half-written. The previous tree stays as it was, nothing is shown, and the rebuild is retried once a moment later. Only a first load with nothing to keep shows the existing error node.
- The scroll position is preserved, as far as keeping the selected node visible allows.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `tree-view`: adds a requirement that a refresh of the Changes tree preserves the user's selection and expansion by node identity, clears the selection only for a node that no longer exists (keeping the workflow panel in step), and keeps the existing tree when a rebuild fails.

## Impact

- **Code:**
  - `toolwindow/OpenSpecToolWindowPanel.java`: `refreshAsync`, the expansion save and restore, and the selection listeners.
  - `toolwindow/SpecTreeModel.java`: a stable identity for `TreeNodeData`.
  - `toolwindow/WorkflowActionPanel.java`: a way to clear the active change.
  - No change to what the tree shows, its labels, or when it refreshes.
- **Tests:** plain JUnit over Swing trees, covering a rebuild with a changed label, a removed node, a failed build, and the preview not blanking. Each must fail against today's code.
- **Platforms:** no change to IntelliJ 2024.2+ compatibility. The approach is chosen to avoid internal or experimental platform API.
