## Context

The Browse tab's preview pane has these routes today (`SpecPreviewRenderer.classify`):

| Node | Preview |
|---|---|
| change | `CHANGE_DELTAS` |
| `ARTIFACT` / `ARTIFACT_DONE` with a `.md` path | `CHANGE_ARTIFACT` (the plain markdown) |
| `specs` artifact | its glob routes to `CHANGE_DELTAS` (the specs-artifact fix) |
| `DELTA_SPEC` | badged markdown |
| everything else, including ready, blocked and missing artifacts | `NONE` |

`NONE` shows one fixed sentence, "Select a change to see its consolidated deltas." `MAIN_SPEC` survives in the enum and in `renderMarkdown`, but nothing routes to it.

The deltas render is cached per change name (`changeDeltasCache`). The pane's diff links resolve against `deltasChangeDir`, set when a deltas fragment is applied. Tree state comes back through `TreeState` in `applyModel`. The first real model replaces a "Loading..." placeholder, so there is no expansion to restore and `Changes` stays collapsed.

Artifact nodes come from one of two places:
- the CLI DAG, where `changeName` is set and the path is `changeDir + "/" + outputPath`, which can be a glob;
- the no-CLI fallback, where `changeName` is null.

Either way, the enclosing change node carries both the change's name and its directory.

## Goals / Non-Goals

**Goals:**
- Any node under a change shows that change's deltas, with a way into the artifact's file.
- Every pane state names itself and says what it's for.
- The empty state teaches a newcomer how to use the pane.
- `Changes` is open on the first load.

**Non-Goals:**
- the wrong "install the CLI" message when the CLI rejects a change, which is a separate tracker entry;
- the validation-status line and the adaptive splitter, which build on this header later;
- scrolling the deltas view to the selected delta spec's capability;
- the remaining walkthrough drift, owned by the walkthrough tracker entry.

## Decisions

1. **Route on the enclosing change, not the node's path.**
   - **Routing:** `classify(type, filePath, changeDir)` returns `CHANGE_DELTAS` for a change node, and for any artifact node (`ARTIFACT`, `ARTIFACT_DONE`, `ARTIFACT_READY`, `ARTIFACT_BLOCKED`, `MISSING_ARTIFACT`) whose `changeDir` is known. `DELTA_SPEC` keeps its markdown render; everything else is `NONE`.
   - **Selection snapshot:** `snapshotSelection` takes `changeDir` and the change name from the enclosing change node (`SpecTreeModel.resolveChangeName`), so a fallback-tree artifact with a null `changeName` works.
   - **Globs:** the glob special case reduces to one rule: a glob path is never a file, so it gets no open link.
   - *Alternative:* keep the markdown render and add a header to it. Rejected: it keeps a worse copy of the editor in a narrow pane, which is exactly what the review asked about.
2. **The open-in-editor link is added at apply time, not cached.**
   - **Selection:** `PreviewSelection` gains `openFilePath`. It is the artifact's path when that path is not a glob, otherwise null.
   - **Existence check:** the off-EDT render checks that the file exists before offering the link.
   - **Insertion:** a pure `SpecPreviewRenderer.withOpenLink(fragment, relativePath)` inserts `<a href="openspec-open:<relative path>">Open <file> in editor</a>` directly after the header. It runs on a cached or fresh deltas fragment and on the CLI-unavailable placeholder. The cache stays keyed on the change alone.
   - **Click handling:** the pane's hyperlink listener resolves the path against `deltasChangeDir` and opens it with `OpenFileDescriptor`. A path that is absolute or contains `..` is ignored.
   - *Alternative:* cache per artifact. Rejected because it multiplies CLI calls for the same deltas.
3. **One header, in every fragment.**
   - **Fragments:** `SpecPreviewRenderer.header(changeName)` renders a stable marker class (`openspec-preview-header`), "Change Deltas — `<change>`" (or just "Change Deltas" with nothing selected) and the one-line purpose.
   - **Callers:** `renderChangeDeltas`, the no-deltas state, `cliUnavailablePlaceholder(changeName)` and `emptyState(hasChanges)` all start with it.
   - **Name:** the deltas header names the change id rather than the proposal title, so every state of the same change reads the same.
4. **The empty state knows whether there are changes.**
   - **Count:** `applyModel` records whether the `Changes` node has any change children (the no-changes tree holds only a `HINT`).
   - **Propose link:** with no changes, the empty state carries `openspec-action:propose`. The hyperlink listener runs it through `ActionLauncher` with the `OpenSpec.Propose` action.
   - **No expand link:** decision 5 keeps `Changes` open, so a link to expand it would be redundant.
5. **Expand `Changes` once, on the first load.**
   - **When:** after `state.applyTo(tree)`, when `treeLoaded` is still false and `Changes` has a change child, `applyModel` expands the `Changes` path.
   - **Later refreshes:** they skip this step, so `TreeState` carries the user's later expand or collapse unchanged.
6. **Delete the dead kinds.** `MAIN_SPEC` and `CHANGE_ARTIFACT` are removed, along with their `renderMarkdown` branches and the tests that exercised only them. `renderMarkdown` remains for `DELTA_SPEC`.

## Risks / Trade-offs

- **Without the CLI, an artifact no longer previews its markdown.** It shows the CLI-unavailable placeholder, with the header and the open link. → Acceptable: the editor is one click away, and delta specs still preview without the CLI.
- **A second seeded demo change can shift UI smoke expectations.** → Name it after `demo-add-farewell` alphabetically, leave `demo-add-farewell` untouched, and run the whole `uiSmoke` suite before the PR.
- **`TreeRefreshStateTest` uses an artifact's markdown render** as its "preview survived" signal. → Switch that test to a delta-spec node, which still renders from the file and needs no CLI.
