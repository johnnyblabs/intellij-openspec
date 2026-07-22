## Why

The OpenSpec tool window lists specs and changes as a tree of names, but you cannot read what is *inside* a node without leaving the tool window and opening the file. Now that the spec parser agrees with the CLI and exposes requirement bodies and scenario text, we can turn "a list of names" into "browse and read": a rendered preview beside the tree, plus search that reaches into requirement content — the headline of the "Spec Intelligence & Viewing" release. This is the first feature that makes the trustworthy parse *visible*.

## What Changes

- Add a **read-only rendered-markdown preview pane** to the right of the existing Browse tree, in a horizontal splitter (collapsible right pane; splitter proportion persisted). Single-click a node renders its document; double-click still opens the real file in the editor (unchanged).
- Preview renders per node type: **main specs**, **change artifacts** (proposal/design/tasks), and **delta specs**. Always read-only — editing stays in the editor.
- **Delta operation badges:** in a rendered delta-spec preview, the `ADDED`/`MODIFIED`/`REMOVED`/`RENAMED` operation headers are visually badged so a change's proposed deltas read at a glance.
- **Requirement anchoring:** selecting a Requirement node scrolls the preview to that requirement's section.
- **Full-text search:** the Browse search box, which today matches node labels only, is widened to also match **requirement bodies and scenario text** — "find where rate-limiting is specified," not just "find the requirement named X." Results still present as the filtered tree.
- Reuses the plugin's existing markdown-render stack (commonmark → theme-aware HTML in a Swing `JEditorPane`); **no new dependency and no new extension point**.

## Capabilities

### New Capabilities
- `spec-viewer`: The read-only rendered-markdown preview surface beside the Browse tree — per-node-type rendering (main spec / change artifact / delta spec), delta operation badges, requirement-section anchoring, and the on-model boundaries the preview must respect (render source markdown; never synthesize spec truth or show per-spec status/coverage).

### Modified Capabilities
- `tree-view`: The "Search and filtering" requirement is extended so filtering matches requirement **body and scenario text** in addition to node labels, while preserving the existing real-time, auto-expand, case-insensitive behavior.

## Impact

- **Affected code:** `OpenSpecToolWindowPanel` (host a `OnePixelSplitter` with the new `JEditorPane` preview + a selection listener), `SpecTreeModel` (extend `filterNode`/model build to match requirement/scenario content — nodes already carry `filePath`), reuse of `MarkdownHtmlRenderer` and the `ExplorePanel` render pattern. `SpecParsingService` is consumed (bodies/scenarios), not changed.
- **Platform stack (lowest-risk, multi-IDE-safe):** Swing `JEditorPane` + `HTMLEditorKitBuilder` + the existing commonmark renderer; `OnePixelSplitter`; off-EDT read+parse+render with `invokeLater`. **No JCEF**, **no `JBHtmlPane`**, **no dependency on the bundled Markdown plugin** — each is a forward-compat or not-in-every-IDE trap. No PSI/ReadAction; dumb-mode-safe.
- **Platform compatibility:** unchanged. All APIs used are present and non-deprecated on IntelliJ IDEA 2024.2; min-platform holds at 2024.2 and `verifyPlugin` is not implicated by the recommended stack.
- **On-model boundary:** the viewer renders client-owned files and reflects CLI-sourced change state; it does **not** compute or show per-spec coverage/status, code↔spec links, a persisted search index, or a synthesized post-archive spec.
- **Follow-ups (out of scope):** highlight the search term in the preview, `tasks.md` GFM checkbox rendering, archived-change full-text search, an editor-based full-width rendered view.
- **Tracker:** linked to an existing tracker issue via the gitignored `.tracking.yaml` sidecar, per the repository's tracker-sidecar convention.
