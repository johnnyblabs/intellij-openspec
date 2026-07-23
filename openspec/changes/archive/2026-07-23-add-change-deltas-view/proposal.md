## Why

The viewer renders individual spec, artifact, and delta-spec files, but selecting a **change** node in the Browse tree renders nothing — a change node's path is a directory, so the preview falls to its empty state. Yet "what does this change actually modify at the spec level?" is the central question when inspecting a change, and the OpenSpec CLI already answers it: `openspec show <change> --json` emits the change's assembled deltas. This fills the blank slot with a consolidated, at-a-glance deltas view — the next step in "Spec Intelligence & Viewing," reusing the render pipeline and operation badges the viewer already ships.

## What Changes

- Selecting a **Change node** renders a **consolidated deltas document** in the existing preview pane: a header (`Deltas — <change>` + a one-line summary of counts), then each **capability** section, then each **operation** group (ADDED / MODIFIED / REMOVED / RENAMED) with the existing badges, then each requirement's text and scenarios.
- The assembled delta set is **sourced from the OpenSpec CLI** (`openspec show <name> --type change --json`) — never hand-assembled — and grouped by capability, then by operation, with the plugin imposing its own stable cross-capability sort.
- Each capability section **cross-links to the existing `DeltaSpecDiffAction`** (the machine diff of that capability's delta vs. the current main spec), so the consolidated *reading* view and the per-capability *diff* view complement each other.
- The preview empty-state copy is updated so change-node selection is self-describing.
- When the CLI is unavailable (built-in mode), the consolidated view shows an informative placeholder rather than hand-assembling deltas; per-node delta-spec file preview is unaffected.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `spec-viewer`: Adds requirements for (1) a consolidated, CLI-sourced deltas view rendered when a change node is selected — grouped by capability then operation, badged, handling the RENAMED and empty cases, with a stable cross-capability sort and the on-model fences; and (2) a cross-link from each capability section to the delta-vs-main diff.

## Impact

- **Affected code:** `SpecPreviewRenderer` (new `CHANGE_DELTAS` kind + a `renderChangeDeltas` assembler), `OpenSpecToolWindowPanel` (route a `CHANGE` node to the new kind, add a `HyperlinkListener` for the diff cross-link, update the empty-state copy and the accessible-name marker), a small parse of the change-show JSON into the delta model (`DeltaSpecOperation` already carries the operation enum). Reuses `MarkdownHtmlRenderer`, `DeltaBadgeDecorator`, `RequirementAnchors`, `CliRunner`, and `DeltaSpecDiffAction`.
- **Source of truth:** the assembled deltas come from the CLI (`show <change> --json`, stdout only); individual delta-spec markdown files may still be read directly for the per-node preview and the diff. The view never synthesizes a post-apply "effective" spec and never shows per-delta status/coverage.
- **No new dependency, no new extension point.** Runs off the EDT in the existing preview pipeline (generation-token guarded); a per-change render cache avoids re-spawning the CLI on re-selection.
- **Platform compatibility:** unchanged — reuses the multi-IDE-safe Swing stack; min-platform holds at 2024.2; `verifyPlugin` not implicated.
- **Behavior change users will see:** clicking a change now shows its deltas instead of a blank pane.
- **Follow-ups (out of scope):** operation filter, side-by-side consolidated delta-vs-main across all capabilities, copy/export deltas, built-in-mode consolidated rendering.
- **Tracker:** linked to an existing tracker issue via the gitignored `.tracking.yaml` sidecar, per the repository's tracker-sidecar convention.
