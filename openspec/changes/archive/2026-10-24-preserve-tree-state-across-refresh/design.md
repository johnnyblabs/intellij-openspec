## Context

`OpenSpecToolWindowPanel.refreshAsync()` rebuilds the whole Changes tree off the EDT, then calls `tree.setModel(model)` on the EDT. `JTree.setModel` clears the selection first, and nothing reselects afterwards, so the preview listener renders the empty state. Expansion is restored by `pathToKey`, which joins each node's `toString()`, its label. Labels carry changing text (a change's `X/Y` task count, a blocked artifact's `(needs: …)`), so those nodes collapse. Two more defects are in the same path:
- The expansion state is captured when the refresh *starts*, before seconds of CLI work, so anything the user expands or selects meanwhile is overwritten.
- Overlapping refreshes (the VFS alarm, plugin actions, manual Refresh) can apply an older model last.

A failed build replaces the tree with an error node.

The platform's `TreeState` already saves and restores expansion and selection across a model swap. It matches nodes by `PathElementIdProvider.getPathElementId()` when the user object implements it, and by `toString()` otherwise, and the `toString()` fallback is the defect here. `TreeState.createOn(JTree, TreePath)`, `applyTo(JTree)`, `setScrollToSelection(boolean)` and `PathElementIdProvider` are public, with no deprecated, internal or experimental marking, from 2024.2 through 2026.2. This was checked with `javap` against the IC 2024.2.6 and IU 2026.2.3 jars.

## Goals / Non-Goals

**Goals:**
- Selection and expansion survive every rebuild.
- The preview never flashes empty for a node that still exists.
- A node that is really gone clears the selection.
- A failed rebuild keeps the tree.
- The newest refresh always wins.

**Non-Goals:**
- Changing what the tree shows, its labels or refresh triggers.
- Moving to `StructureTreeModel`/`AsyncTreeModel`.
- Diffing the model in place.
- Keeping the preview's scroll offset when the same node re-renders.

## Decisions

1. **`TreeState` plus a stable node identity (option B), keeping the full rebuild.**
   - `SpecTreeModel.TreeNodeData` implements `PathElementIdProvider`. The id is made from fields that don't change as the node's status changes, never from the label or the raw `TreeNodeType`, because the type itself moves: CHANGE → CHANGE_DONE → CHANGE_ARCHIVE_READY, ARTIFACT_READY → ARTIFACT_DONE, MISSING_ARTIFACT → ARTIFACT.
     - `changes` for the CHANGES group;
     - `change:<changeName>` for any change type;
     - `artifact:<artifactId>` for artifact types, falling back to the label for the CLI-absent nodes that have no artifact id;
     - `delta:<capability>` for DELTA_SPEC;
     - `hint` for HINT.
   - `toString()` keeps returning the label, which the renderer and accessibility use.
   - *Alternatives:*
     - Hand-rolled keys around `setModel` re-implement `TreeState`'s matching.
     - `StructureTreeModel` is a large rewrite. Its default invoker would run the CLI calls inside a read action, and it still needs the same identity.
     - Diffing in place needs a keyed reconcile at every level, and `nodeStructureChanged` drops descendant state anyway.
     - `TreeUtil.restoreExpandedPaths` does nothing across a model swap, because `DefaultMutableTreeNode` compares by identity.
2. **Capture the state at swap time, not at refresh start.** Inside the EDT `invokeLater`:
   1. Call `TreeState.createOn(tree, new TreePath(root))`. Not `createOn(tree)`, which doesn't save the selection.
   2. Call `setScrollToSelection(false)`.
   3. Swap the model and call `applyTo`.

   With a `DefaultTreeModel` that's all synchronous on the EDT.
3. **A guard flag around the swap.** `restoringTree` is set only during `setModel` plus `applyTo`. Both selection listeners return early while it's set. After the swap, the panel runs `schedulePreview()` once and syncs the workflow panel once, so there's no transient empty render and no second CLI `show`. Without the guard there would be no visible flash anyway (the 120 ms preview `Alarm` is cancelled by the reselect, and stale generations are dropped), but there would be extra work.
4. **A selection that vanished clears the selection.** If nothing is selected after `applyTo`, the empty state is right. If something is selected, its id is compared with the id saved before the swap; on a mismatch, `clearSelection()` is called. The mismatch happens because `TreeState` falls back to a sibling when the saved node is gone.
   - The preview then shows the empty state.
   - The workflow panel is refreshed. Its `refresh()` already drops a change that no longer exists.
   - This follows the spec ("cleared only when that node is gone"), not Project View's select-a-sibling behaviour. Previewing a sibling the user never chose would be surprising.
5. **Keep the scroll position.** `setScrollToSelection(false)` stops `TreeState` re-centring on every refresh. The panel scrolls the selected row into view only when it is off-screen.
6. **The newest refresh wins.** An EDT-incremented `treeGeneration` is captured when each refresh starts. A result whose generation is stale is discarded on the EDT.
7. **A failed build keeps the tree, silently, and retries once.**
   - `buildModel()` already handles the expected failures itself:
     - a CLI error or timeout makes that change fall back to file-based nodes;
     - per-file I/O errors are caught;
     - bad paths are ignored.
   - So an exception that escapes is an unexpected runtime error, in practice a race: a change directory removed by a `git checkout` or an agent mid-scan, or a file half-written. The same burst of file changes fires further refreshes, so the next one normally succeeds.
   - When a tree is already loaded, the panel logs the exception (WARN) and leaves the tree, selection and expansion untouched. It shows nothing to the user and schedules **one** quiet retry about a second later on the existing refresh `Alarm`, which covers the case where the failing refresh was the last event of the burst.
   - If the retry also fails, it stays silent. The next file change or a manual Refresh rebuilds.
   - Only the first load, with nothing to keep, shows the existing "Error loading — click Refresh to retry" node. Its root user object stays `"OpenSpec"` (the hint handler matches on it).
   - *Alternative rejected:* a "Refresh failed" status message. It would announce a transient race the user can't act on and that fixes itself, with no extra information for them.
8. **Fix the workflow listener's path source.** It reads `tree.getSelectionPath()` instead of `e.getPath()`. `e.getPath()` returns the *removed* path on a clear, so the listener reacts to removals.

## Risks / Trade-offs

- **New `com.intellij.*` API references:** `TreeState` and `PathElementIdProvider`. → `verifyPlugin` across the full IDE range gates this, and the pre-push hook runs it automatically.
- **Two artifacts with the same id under one change.** Not possible: artifact ids are unique per change, and a delta's capability is prefixed `delta:` so it can't collide with the `specs` artifact.
- **A CLI-absent node flips to a CLI-backed node** (artifact id `null` → `proposal`, label `proposal.md`). The id changes, so the selection clears once when CLI availability changes. → Acceptable and rare. Optionally the label can be normalised later.
- **`TreeState`'s matching rules could change in a future platform.** → The behaviour tests pin it, so a change fails loudly.

## Migration Plan

None. This is UI state only; nothing is persisted or changed on disk.

## Open Questions

None.
