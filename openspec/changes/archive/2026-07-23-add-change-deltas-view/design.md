## Context

The shipped viewer (`add-searchable-spec-viewer`) renders individual files in a preview pane driven by tree selection: `SpecPreviewRenderer.classify(nodeType, path)` picks a `PreviewKind`, an off-EDT read renders the markdown, and `invokeLater` sets the pane (generation-token guarded). A **change** node's `filePath` is the change *directory*, so `classify` returns `NONE` and selecting a change shows the empty state. This change fills that slot with a consolidated deltas view.

An advisory pass established the model: a change's deltas are a first-class CLI output (`openspec show <change> --json` → `{id, title, deltaCount, deltas[]}`), with full requirement text and scenario `rawText` grouped by capability. The cross-capability *assembly* is CLI-owned (the CLI walks the change's `specs/` tree); the plugin must not re-derive it. The plugin already has `DeltaSpecOperation.OperationType` (the four operations), `DeltaBadgeDecorator` (operation badges), `DeltaSpecDiffAction` (per-capability delta-vs-main diff), and `CliRunner`.

## Goals / Non-Goals

**Goals:**
- Render a change's assembled deltas, grouped capability → operation, in the existing preview pane, reusing the render/badge pipeline.
- Source the delta set from the CLI; never hand-assemble it.
- Cross-link each capability to the existing diff action.
- Degrade cleanly with no CLI and on empty/rename/removed edge cases.

**Non-Goals:**
- No synthesized post-apply "effective" spec; no per-delta status/coverage.
- No new dependency, extension point, tool window, or tab.
- No replacement of `DeltaSpecDiffAction` — it stays the machine diff.
- No operation filter / export / built-in-mode consolidated rendering (follow-ups).

## Decisions

**Decision 1 — Bind to the existing `CHANGE` node selection; add `PreviewKind.CHANGE_DELTAS`.**
`classify` routes a `CHANGE` node to the new kind; the preview pipeline calls a new `renderChangeDeltas`. *Alternatives:* a dedicated "Deltas" tree node (duplicates the existing per-capability `DELTA_SPEC` children), a separate popup/dialog (fragments the master/detail viewer), or folding into `DeltaSpecDiffAction` (that is a machine diff, a different job). The change node is already the natural "everything this change does" anchor and already renders empty — filling it is the intuitive completion.

**Decision 2 — Source the assembled deltas from the CLI (`show <name> --type change --json`), stdout only.**
The plugin parses the CLI's `deltas[]` into a small model and assembles an HTML fragment; it does not run its main-spec parser over the change's `specs/` tree. Do not pass `--deltas-only` (a 1.6 no-op) or the deprecated `--requirements-only` alias. Read stdout only (the CLI prints a spurious flag warning to stderr). Use the verb form (`show`), not the deprecated noun form (`change show`).

**Decision 3 — Group by capability, impose a stable cross-capability sort, preserve within-capability operation order.**
Within a capability the CLI's order is deterministic (ADDED → MODIFIED → REMOVED → RENAMED, then authored order) and is preserved. Across capabilities the CLI order is `readdir`-dependent (not a contract), so the plugin sorts capability groups itself (alphabetical). Rendering to HTML lets `DeltaBadgeDecorator` badge the operation headers and `RequirementAnchors` provide anchor-scroll — same pipeline as the file preview.

**Decision 4 — Reuse the off-EDT preview pipeline + a per-change render cache.**
The CLI call runs in the existing `previewAlarm` pooled-thread path, generation-token guarded, `invokeLater` to set the pane. Because a subprocess per selection is costly, cache the rendered fragment per change name, invalidated by the existing VFS listener, so re-selecting a change doesn't re-spawn the CLI. Set a `CHANGE_DELTAS` accessible-name marker (alongside `PREVIEW_RENDERED_NAME`) so the uiSmoke journey can assert the render fired.

**Decision 5 — Cross-link via a `HyperlinkListener` on the `JEditorPane`.**
Capability section headers emit anchors that a `HyperlinkListener` maps to `DeltaSpecDiffAction` for that capability. Standard on 2024.2; keeps the reading view and the machine diff distinct but connected.

**Decision 6 — CLI-gated with a placeholder in built-in mode.**
With no CLI the consolidated view can't be assembled on-model, so it shows a placeholder. Per-node `DELTA_SPEC` file preview (reading the file directly) still works in built-in mode. The built-in coverage gap is accepted and documented.

## Risks / Trade-offs

- **Subprocess latency on selection** → mitigated by the per-change cache + off-EDT execution; the pane shows a brief loading/empty state until the fragment arrives.
- **RENAMED shape differs** (no `requirement`) → a null-safe branch; covered by a rename-only fixture + test.
- **Built-in-mode gap** (no consolidated view without CLI) → accepted; placeholder is honest and on-model, and per-file delta preview still works.
- **Contract drift** if the CLI JSON changes → covered by a captured real fixture + contract test; re-capture on CLI bumps.
- **Stderr noise** (spurious flag warning) → parse stdout only; the runner must not treat stderr as failure output for this call.

## Migration Plan

Additive; no user migration, no schema/dependency/data change. Steps: (1) add `CHANGE_DELTAS` kind + `classify` routing; (2) parse the change-show JSON into a delta model; (3) `renderChangeDeltas` assembles the grouped, badged fragment; (4) wire the CLI call + cache into the preview pipeline; (5) add the `HyperlinkListener` cross-link + empty-state copy + accessible marker; (6) capture the CLI fixture + contract test + unit/uiSmoke/screenshot. Rollback is a straight revert.

## Open Questions

- **Cache invalidation granularity:** invalidate a change's cached fragment on any change to `changes/<name>/specs/**`; confirm the existing VFS listener already covers that path or extend it.
- **Anchor encoding for the diff cross-link:** encode the capability id in the anchor href (e.g. `openspec-diff:<capability>`) and resolve it in the `HyperlinkListener`; finalize the scheme during implementation.
