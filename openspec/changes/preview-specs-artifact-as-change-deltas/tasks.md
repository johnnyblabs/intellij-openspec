## 1. Routing

- [x] 1.1 In `SpecPreviewRenderer.classify`, treat an artifact path containing `*`, `?` or `[` as a glob. A glob under the change's `specs/` directory classifies as `CHANGE_DELTAS`; any other glob classifies as `NONE`. Verify with `SpecPreviewRenderTest`:
  - the `specs` artifact's path, built as `changeDir + "/" + outputPath` from the **captured** `outputPath` in `fixtures/cli/status-with-context.json` (not a hand-typed glob), classifies as `CHANGE_DELTAS`;
  - a glob like `notes/**/*.md` classifies as `NONE`;
  - `proposal.md` and `design.md` still classify as `CHANGE_ARTIFACT`.

  Each assertion must fail against today's `classify`.
- [x] 1.2 In `OpenSpecToolWindowPanel.snapshotSelection`, for a `CHANGE_DELTAS` selection on an artifact node, use the enclosing change node's directory as the preview's `filePath` (the deltas view builds its diff links from it), and the node's `changeName` as the CLI key. Make `snapshotSelection` package-private and static so it can be tested without the CLI: `CliRunner` has no seam, and the deltas render itself is already covered by `ChangeDeltasRenderTest` against captured `show-change.json`. Verify with a test that a `specs` artifact `TreePath` (change node with its directory as path, then the artifact with the captured glob path) snapshots to kind `CHANGE_DELTAS`, the change directory and the change name. It must fail against today's code, which yields `CHANGE_ARTIFACT` and the glob path.

## 2. Verify

- [x] 2.1 `./gradlew build` is green.
- [x] 2.2 `openspec validate preview-specs-artifact-as-change-deltas --strict` is clean.
- [x] 2.3 Add a CHANGELOG `## Unreleased` Fixed entry: selecting a change's specs node now shows the change's deltas instead of an empty preview.

## Workflow follow-up

- Archive the change, then close the tracker entry.
