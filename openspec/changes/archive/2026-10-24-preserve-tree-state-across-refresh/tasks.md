## 1. Stable node identity

- [x] 1.1 Make `SpecTreeModel.TreeNodeData` implement `com.intellij.ide.util.treeView.PathElementIdProvider`, with ids as in design decision 1:
  - `changes`;
  - `change:<changeName>`;
  - `artifact:<artifactId>`, falling back to the label;
  - `delta:<capability>`;
  - `hint`.

  Keep `toString()` as the label and leave `getPathElementType()` at its default. Verify with a JUnit test, which must fail if the id is derived from the label or the type:
  - a change node's id is unchanged when its label goes from `x 1/3` to `x 2/3` and its type goes from CHANGE to CHANGE_ARCHIVE_READY;
  - an artifact's id is unchanged from ARTIFACT_READY to ARTIFACT_DONE and when a `(needs: …)` suffix appears;
  - the `specs` artifact and a `specs` delta capability get different ids.

## 2. Refresh restores state by identity

- [x] 2.1 Rework `OpenSpecToolWindowPanel.refreshAsync()` as in design decisions 2, 3, 5 and 6.
  - Add an EDT-incremented `treeGeneration`, and discard stale results.
  - At swap time on the EDT, call `TreeState.createOn(tree, new TreePath(root))` and `setScrollToSelection(false)`. Then, with `restoringTree` set, call `setModel` and `applyTo`.
  - Scroll the selection into view only when it is off-screen.
  - Call `schedulePreview()` once and sync the workflow panel once.
  - Remove `saveExpansionState`, `restoreExpansionState`, `restoreNodeExpansion` and `pathToKey`.

  Verify with `./gradlew test`.
- [x] 2.2 Clear a vanished selection (design decision 4). After `applyTo`, if the selected node's id differs from the id saved before the swap, call `clearSelection()`, then show the empty preview and refresh the workflow panel.
- [x] 2.3 Make the tree-to-workflow selection listener read `tree.getSelectionPath()` instead of `e.getPath()`, and make both selection listeners return early while `restoringTree` is set (design decisions 3 and 8).
- [x] 2.4 Keep the tree when a build fails, silently (design decision 7). With a tree already loaded:
  - log the failure at WARN;
  - leave the model, selection and expansion untouched;
  - show nothing to the user;
  - schedule one retry about a second later on the refresh `Alarm` (not a loop: a retry that fails again doesn't schedule another).

  Only the first load shows the existing error node, with root user object `"OpenSpec"`.

## 3. Behaviour tests

`TreeRefreshStateTest` runs the real refresh path (pooled build, then the EDT swap) with tree models the test supplies through the panel's model-builder constructor. Previews read real temporary `.md` files from disk.

**How each test was proven:** `git stash` can't compile the new tests against the old code, so each was proven by a mutation applied to the finished code and then restored. Each mutation fails the tests listed:

| Mutation | Fails |
|---|---|
| The old swap, with no `TreeState` restore | 3.1, the during-build test, the retry-restore test |
| No clear-on-mismatch | 3.2 |
| No generation check | 3.4 |
| The error node on every failure | 3.3 |
| No retry, or a retry that reschedules itself | 3.3 |
| State captured at refresh start | the during-build test |
| The listener reading `e.getPath()` | the listener test |
| An id built from the label | 1.1 and the artifact-status test |

3.2 passes on the old code too, because the old code cleared every selection. Its bite is the clear-on-mismatch mutation.

**Added beyond the original list:**
- the selected artifact's own blocked→done and `(needs: …)` change;
- state the user changes *during* a build is kept (design decision 2);
- a clear doesn't switch the workflow panel back (decision 8);
- an identity test over real labels built from the captured `1.7.0` status output.

**Known limits:**
- The `restoringTree` guard isn't pinned by a behaviour test. Without it the preview still doesn't flash, because the reselect cancels the pending empty render; it only saves redundant work.
- Models are hand-built from `TreeNodeData`. The swap logic doesn't depend on CLI output, and the real node shapes are covered by the captured-label identity test.
- The workflow panel switching away from a vanished change comes from its existing `refresh()`, which drops changes `ChangeService` no longer lists. The light test project has no such changes, so 3.2 doesn't assert it.

- [x] 3.1 Select a change's artifact and expand the change. Rebuild with a model where that change's task count and status changed. Assert:
  - the change is still expanded;
  - the same artifact is selected;
  - once the preview alarm drains, the preview's accessible name is the rendered name, not `PREVIEW_EMPTY_NAME`.
- [x] 3.2 Select a change, then rebuild with a model that no longer has it. Assert the selection is empty, not a sibling, and the preview shows the empty state.
- [x] 3.3 Make a rebuild fail while a tree is loaded. Assert:
  - the previous model instance, selection and expansion are unchanged;
  - the status strip text is unchanged;
  - exactly one retry runs, and a retry that succeeds swaps in its model with the state restored.

  Separately, a failure on the first load shows the error node.
- [x] 3.4 Start two refreshes and complete them out of order. Assert the tree ends on the newer model.

## 4. Verify

- [x] 4.1 `./gradlew build` is green, including the coverage floor.
- [x] 4.2 `./gradlew verifyPlugin` across the configured IDE range reports Compatible, with no new deprecated, internal or experimental API usages. This change adds `TreeState` and `PathElementIdProvider` references.
- [x] 4.3 Run `caffeinate -dimsu ./gradlew uiSmoke` and confirm it is green. Then do a manual check in a sandbox IDE (`lifecycle-testdrive`):
  - select a change's artifact;
  - tick a task in that change's `tasks.md` from outside the IDE;
  - return to the IDE and confirm the selection, expansion and preview stay.
- [x] 4.4 `openspec validate preserve-tree-state-across-refresh --strict` is clean.
- [x] 4.5 Add a CHANGELOG `## Unreleased` Fixed entry: "The OpenSpec tool window keeps your selection and expanded changes when it refreshes, instead of going blank". Keep it user-facing.

## Workflow follow-up

- Archive the change, then close the tracker entry.
