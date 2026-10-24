# Proposal

## Why

A Marketplace reviewer asked why the tool window has a markdown preview at all. The question is fair, because a new user with existing changes sees this:

- **`Changes` starts collapsed.**
- **The narrow right-hand pane says only** "Select a change to see its consolidated deltas", which tells a newcomer nothing.
- **Clicking proposal, design or tasks shows plain markdown** in that narrow pane, a worse copy of the editor.
- **Clicking an artifact that doesn't exist yet,** for example a blocked `tasks`, shows that same "Select a change…" message although a change's node was just selected.

The plugin's one unique view, the consolidated **Change Deltas** of a change, only appears when the change row itself is selected. Nothing names it or says what it's for. Tracked by the linked tracker entry.

## What Changes

- **Every node under a change previews that change's deltas.** Selecting any artifact (proposal, design, tasks, specs, done or not yet created) shows the same Change Deltas view as selecting the change. The plain-markdown artifact render is retired. A delta-spec node keeps its own badged render, which works without the CLI.
- **"Open `<file>` in editor" link.** When the selection is an artifact that exists as a single file, the view starts with a link that opens it. Double-click still opens it too.
- **The pane says what it is, in every state.** Each state starts with a "Change Deltas — `<change>`" header and one line: "What this change will add, modify or remove in your specs when it's archived." This covers deltas, no deltas, CLI unavailable, and nothing selected.
- **An empty state that explains the pane.** With nothing selected, the pane explains what a change is and what the pane shows, and says to single-click a change to see its deltas and double-click a file to edit it. When the project has no changes, it offers a "Propose a change" link.
- **`Changes` is expanded on the first load** when it has at least one change. A later collapse is kept across refreshes.
- **Removed:** the inert main-spec render path.
- **Docs and demo:**
  - The feature reference describes the new behaviour.
  - The spec-browser Marketplace screenshot is retaken.
  - The lifecycle demo gains a second change with a MODIFIED delta, plus a walkthrough stop for the Change Deltas pane.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `spec-viewer`:
  - Artifact selections route to their change's deltas, with an open-in-editor link.
  - The pane carries a purpose header in every state and an explanatory empty state.
  - The rule from the specs-artifact fix ("a glob outside `specs/` shows nothing") is folded into the new per-node-type rule.
- `tree-view`: `Changes` is expanded on the first load.

## Impact

- **Code:**
  - `SpecPreviewRenderer`: classify, the header, the empty states, and removal of `MAIN_SPEC` and `CHANGE_ARTIFACT`.
  - `OpenSpecToolWindowPanel`: the selection snapshot, the open-file link handler, first-load expansion, and the empty state that knows whether the project has changes.
  - Tests: `SpecPreviewRenderTest`, `ChangeDeltasRenderTest`, `PreviewSelectionSnapshotTest` and `TreeRefreshStateTest`. The last of these currently relies on an artifact's markdown render.
- **UI smoke:** Journey 1 and Journey 9 change.
- **Without the CLI,** selecting an artifact now shows the CLI-unavailable placeholder (with the purpose header and the open-in-editor link) instead of the artifact's markdown. Delta-spec nodes still preview without the CLI.
- **Docs and demo:**
  - `docs/feature-reference.md`.
  - The Marketplace screenshot `01-spec-browser`, which must be uploaded by hand.
  - `scripts/seed-lifecycle-demo.sh`, which the UI smoke journeys share, and the lifecycle walkthrough.
- **Platform:** no new platform API. The plugin still supports IntelliJ IDEA 2024.2 and later.
