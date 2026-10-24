## 1. Routing

- [x] 1.1 In `SpecPreviewRenderer.classify(type, filePath, changeDir)`, return `CHANGE_DELTAS` for every artifact type (`ARTIFACT`, `ARTIFACT_DONE`, `ARTIFACT_READY`, `ARTIFACT_BLOCKED`, `MISSING_ARTIFACT`) when `changeDir` is known. Remove `MAIN_SPEC`, `CHANGE_ARTIFACT` and their `renderMarkdown` branches. Verify in `SpecPreviewRenderTest`:
  - each artifact type with a `changeDir` classifies as `CHANGE_DELTAS`, including the captured `specs` glob and a non-`specs` glob;
  - `DELTA_SPEC` still classifies as `DELTA_SPEC`;
  - an artifact with no `changeDir`, and `HINT`, classify as `NONE`.

  Drop the tests that exercise only `MAIN_SPEC`. Each new routing assertion must fail against today's `classify`; the `DELTA_SPEC`, `HINT` and specs-glob assertions are regression guards. Use the real node shapes: a `MISSING_ARTIFACT` has a null path.
- [x] 1.2 In `snapshotSelection`, take the change name and directory from the enclosing change node, and add `openFilePath` to `PreviewSelection`: the artifact's path, or null for a glob. Verify in `PreviewSelectionSnapshotTest`:
  - a `proposal` node gives `CHANGE_DELTAS`, the change directory, the change name and `openFilePath` = the proposal path;
  - a blocked `tasks` node with no file gives `CHANGE_DELTAS`;
  - a no-CLI fallback artifact with a null `changeName` gets the change name from its parent;
  - the `specs` glob gives a null `openFilePath`.

## 2. Header, open link and empty state

- [x] 2.1 Add `SpecPreviewRenderer.header(changeName)`, with marker class `openspec-preview-header`, "Change Deltas" plus the change name when there is one, and the one-line purpose. Start `renderChangeDeltas`, the no-deltas state, `cliUnavailablePlaceholder(changeName)` and `emptyState(hasChanges)` with it.
  - The deltas header names the change id. Update `ChangeDeltasRenderTest`'s header assertion.
  - Verify that every fragment carries the header marker. The deltas, no-deltas and CLI-unavailable fragments must name the change.
- [x] 2.2 Write `emptyState(hasChanges)`. It explains what a change is and what the pane shows, says to single-click a change and double-click a file, keeps `EMPTY_STATE_MARKER`, and carries an `openspec-action:propose` link only when `hasChanges` is false. Verify both variants in `SpecPreviewRenderTest`, including that the link is absent when there are changes.
- [x] 2.3 Add a pure `SpecPreviewRenderer.withOpenLink(fragment, relativePath)` that inserts an `openspec-open:<relative path>` link directly after the header. Add the matching parser `openFileFromHref(href)`, which rejects absolute paths and `..`. Verify:
  - the link sits after the header and before the first capability;
  - the parser round-trips `proposal.md`, and rejects `../x.md` and `/etc/x.md`.
- [x] 2.4 Wire both links into the panel. Give the CLI-unavailable state its own accessible name, distinct from the empty state.
  - **Render:** the off-EDT render adds the open link to the deltas and CLI-unavailable fragments when `openFilePath` exists as a file. The cache stays keyed on the change.
  - **Click handling:** the hyperlink listener opens `openspec-open:` paths, resolved against `deltasChangeDir`, with `OpenFileDescriptor`, and runs `openspec-action:propose` through `ActionLauncher`.
  - **Empty state:** `showPreviewEmptyState` passes whether the current model has changes.
  - **Accessible names:** an artifact selection gets the change-deltas names.

  Verify in a new `PreviewLinksPanelTest` (real panel, CLI pinned absent, artifact output paths from `fixtures/cli/status-with-context.json`):
  - an artifact with a file shows its change's CLI-unavailable state with one open link, and one with no file shows no link;
  - with the change's deltas cached, selecting the artifact, then the change, then the artifact again, shows the link once and never on the change;
  - activating the link opens the file, and a `..` path opens nothing;
  - the empty state offers Propose only for a model with no changes.

## 3. First-load expansion

- [x] 3.1 In `applyModel`, on the first load (`treeLoaded` still false) with at least one change under `Changes`, expand the `Changes` path after `TreeState` is applied. Verify in `TreeRefreshStateTest`:
  - the first model with a change shows `Changes` expanded;
  - a model with only the no-changes hint is left alone;
  - collapsing `Changes` and refreshing keeps it collapsed.

  - a failed first load followed by a successful refresh still expands `Changes`.

  The no-changes and collapse assertions are mutation guards for the has-changes and first-load checks.
- [x] 3.2 Switch `TreeRefreshStateTest`'s "selection and preview survive a refresh" test to a delta-spec node. Artifact nodes now need the CLI, while a delta spec still renders from its file and flips the pane to `PREVIEW_RENDERED_NAME`. Keep its never-shows-empty assertion.

## 4. UI smoke and demo

- [ ] 4.1 Journey 1: assert through the tree's expanded paths that `Changes` is expanded with no explicit expand (the workflow label already shows the change name, so text alone proves nothing). Journey 9 (its explicit `expandPath` of `Changes` stays, a no-op once Changes opens expanded): after the change's badged deltas render, click `Changes` and wait for the empty name, then click the `proposal` artifact and wait for "OpenSpec preview change deltas badged" again.
- [x] 4.2 In `scripts/seed-lifecycle-demo.sh`, add an opt-in `--deltas-demo` flag that seeds a second change, `demo-modify-greeting`, with a MODIFIED delta on `greeting` whose header matches the main spec's requirement exactly. It is opt-in, like `--archive-ready-demo`, because a second active change sends Archive to a chooser (breaking Journey 5) and the captured UI smoke fixture must stay unchanged. Leave `demo-add-farewell` untouched. Have the lifecycle-testdrive skill pass the flag, and add "Stop 1b — Change Deltas pane" to its walkthrough template: select the change, then an artifact, then nothing, and use the open link and the diff link.
- [ ] 4.3 Run `caffeinate -dimsu ./gradlew uiSmoke`. Every journey must be green, which confirms first-load expansion shifted no expectations.

## 5. Docs and verify

- [x] 5.1 Update `docs/feature-reference.md` (Rendered Preview Pane) to describe the new behaviour:
  - every node under a change shows its deltas;
  - the open link;
  - the header;
  - the empty state and the Propose link;
  - `Changes` expanded on first load;
  - without the CLI, artifacts show the placeholder.

  Check `docs/marketplace-page.md` and `docs/feature-comparison-matrix.md` for claims about artifact previews.
- [ ] 5.2 Retake the `01-spec-browser` Marketplace screenshot with the screenshot tour, and hand it to the maintainer to upload.
- [x] 5.3 Add a CHANGELOG `## Unreleased` entry that tells users the preview now says what it shows, opens on your changes, and shows a change's deltas for any of its artifacts, with a link to open the file.
- [x] 5.4 `./gradlew build` is green, and `openspec validate clarify-browse-preview-purpose --strict` is clean.

## Workflow follow-up

- Archive the change, then close the tracker entry.
