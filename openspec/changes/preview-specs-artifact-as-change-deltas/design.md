## Context

`SpecTreeModel.addDagArtifactNodes` sets each artifact node's path to `changeDir + "/" + outputPath`, using the CLI's `outputPath` from `status --json`. For the spec-driven `specs` artifact that is the glob `specs/**/*.md` (captured in `fixtures/cli/status-with-context.json`). `SpecPreviewRenderer.classify(type, filePath)` only checks the `.md` suffix, so it returns `CHANGE_ARTIFACT`. Then `renderPreview` calls `findFileByPath` on the glob, gets null, and shows the empty state. With no CLI, the plugin's fallback tree never creates a `specs` artifact node (`ChangeService` skips the `specs/` directory), so only CLI-built nodes have this path.

## Goals / Non-Goals

**Goals:** the `specs` artifact shows its change's deltas; no glob is ever looked up as a file.

**Non-Goals:**
- the empty-state wording, owned by the preview-purpose tracker entry;
- previews for other glob artifacts;
- scrolling the deltas view to anything specific.

## Decisions

1. **Route on the path, in `classify`.** An artifact path containing a glob character (`*`, `?` or `[`) is a glob.
   - If the glob lies under the change's `specs/` directory, the kind is `CHANGE_DELTAS`, because that's where OpenSpec reads a change's deltas from and the deltas view is exactly their consolidated form.
   - Any other glob is `NONE`.
   - *Alternative:* route on `artifactId == "specs"`. Rejected because the artifact id is a schema detail, while the location is what makes the deltas view correct.
2. **Take the change directory from the parent change node.** The deltas view keys the CLI call on the change name (the artifact node carries `changeName`) and builds its diff links as `changeDir + "/specs/<capability>/spec.md"`. For an artifact node, `snapshotSelection` uses the enclosing change node's path (the change directory) instead of the glob path, walking up the selected `TreePath`.
   - *Alternative:* strip the glob suffix from the path. Rejected because it depends on the schema's glob shape.

## Risks / Trade-offs

- A custom schema with a spec glob somewhere other than `specs/` gets no preview. → Acceptable: the deltas view is defined by OpenSpec's `specs/` layout, and a no-preview state is accurate rather than broken.
