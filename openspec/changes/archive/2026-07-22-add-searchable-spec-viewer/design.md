## Context

The OpenSpec tool window's Browse tab is a `JTree` (`SpecTreeModel`) of specs, changes, archives, and config, with a debounced label-filter search box and a vertical tree/`WorkflowActionPanel` split. It can show *names* but not *content* without opening a file. The just-shipped parser (`SpecParsingService`) now recovers requirement bodies and scenario text in parity with the CLI, and `MarkdownHtmlRenderer` (commonmark → theme-derived CSS) already renders markdown to themed HTML in `ExplorePanel` via a Swing `JEditorPane`. This change composes those existing pieces into a master/detail viewer.

The design was pre-validated by three advisory passes: on-model boundaries (what a viewer may/mustn't show and where it must defer to the CLI), UI surface (extend Browse into master/detail vs. a new tab), and platform feasibility (which display/search/threading APIs are lowest-risk across 2024.2+ and the whole JetBrains IDE family).

## Goals / Non-Goals

**Goals:**
- Read specs and change artifacts *in the tool window*, rendered, without opening files.
- Make the now-parsed requirement content searchable, not just node labels.
- Make a change's proposed deltas legible (operation badges) and jump to a selected requirement.
- Reuse existing render/threading patterns; add no dependency and no extension point.

**Non-Goals:**
- No JCEF, no `JBHtmlPane`, no dependency on the bundled Markdown plugin.
- No per-spec coverage/status/score, no code↔spec links, no persisted search index, no synthesized post-archive spec.
- No editor-based full-width rendered view (a possible follow-up).
- No change to `SpecParsingService` or to how the CLI is invoked.

## Decisions

**Decision 1 — Extend the Browse tab into master/detail, not a new tab.**
Wrap the current center in the left component of a horizontal `OnePixelSplitter`; add a read-only `JEditorPane` preview as the right component (collapsible, proportion persisted by key). *Alternatives:* a new "Viewer" tool-window tab (duplicates the tree + search you'd keep in sync — rejected); a second tool window (violates the single-tool-window convention); an editor-based `FileEditorProvider` preview (heavier, competes with the bundled Markdown plugin where present — deferred as a follow-up); a transient popup (doesn't support sit-and-read). The tree already *is* the index; a preview beside it is the minimal delta that tells the story.

**Decision 2 — Swing `JEditorPane` + `HTMLEditorKitBuilder` + `MarkdownHtmlRenderer`; not JCEF/`JBHtmlPane`.**
This mirrors the proven `ExplorePanel` path and works in every IDE with no native process and no bundled-plugin dependency. New code uses `HTMLEditorKitBuilder().build()` (the LAF/HiDPI-correct modern kit) rather than the raw `HTMLEditorKit` `ExplorePanel` uses today. *Rejected:* `JBHtmlPane` (new-in-2024.2 config API churns across later IDEs → forward-compat `NoSuchMethodError`, invisible to `build`/`test`); JCEF (needs `isSupported()` gating, a Swing fallback anyway, and buys nothing for prose/tables/code); the platform Markdown plugin (not bundled in every IDE — the multi-IDE trap). Fidelity ceiling (HTML 3.2: no mermaid/syntax-highlighting) is acceptable for spec markdown.

**Decision 3 — Render source markdown; route delta vs. main specs to their own interpretation.**
The preview renders file bytes, never a reconstruction from the CLI's lossy JSON. A main spec and a change's delta spec are distinct grammars (the CLI itself rejects `## ADDED Requirements` in a main spec and `### Requirement:` outside `## Requirements`), so the viewer classifies by node type/path and never conflates them. Change-owned state it surfaces (delta assembly, task progress) is CLI-sourced.

**Decision 4 — Full-text search in the existing filter, no index.**
Extend `SpecTreeModel`'s off-EDT model build so filtering also matches requirement body/scenario text (already parsed) over the bounded `openspec/` corpus — a linear scan under the existing debounce, no index service, nothing persisted. Presentation stays "filter the tree, auto-expand matches." `TreeSpeedSearch.installOn(tree)` MAY be added for keyboard nav (complementary, label-only).

**Decision 5 — Threading: selection → pooled thread → `invokeLater`.**
Selection event on the EDT hands off to `executeOnPooledThread` (VFS read via `VfsUtilCore.loadText` + commonmark parse + render), then `invokeLater` sets the pane text and resets the caret — the panel's existing `refreshAsync`/`applyFilter` pattern. No PSI ⇒ no `ReadAction`; the feature is dumb-mode-safe. `VirtualFile` handles are not cached across model rebuilds; the path string on each node is re-resolved at selection time.

## Risks / Trade-offs

- **Narrow right pane in a side-anchored tool window** → make the preview collapsible and persist the proportion; users can widen or float the window (as Database/Structure tool windows do).
- **`JEditorPane` HTML fidelity** (no GFM task checkboxes, no fenced-code highlighting) → acceptable for MVP; `tasks.md` renders as plain lists. Checkbox rendering is a follow-up.
- **Forward-compat on newer IDEs** → mitigated by choosing only APIs verified present+non-deprecated on 2024.2 and avoiding `JBHtmlPane`/JCEF; if either is ever adopted, `verifyPlugin` must gate it.
- **CLI-shape drift** for the change/list JSON the viewer reads → covered by captured 1.6.0 contract fixtures; re-capture on CLI bumps.
- **Selection→render EDT wiring** is exactly the class of bug `build`/`test` can't see → a release-gated uiSmoke journey asserts the pane renders on selection.

## Migration Plan

Additive UI; no user migration, no schema/dependency/data change. Rollback is a straight revert. Steps: (1) introduce the splitter + preview pane and selection listener in `OpenSpecToolWindowPanel`; (2) render per node type reusing `MarkdownHtmlRenderer`; (3) add delta badges + requirement anchoring; (4) extend `SpecTreeModel` content matching; (5) capture CLI contract fixtures + tests; (6) add the uiSmoke journey and one screenshot-tour shot.

## Open Questions

- **Requirement anchoring mechanism:** anchor via an injected HTML `id`/`<a name>` per requirement heading and `scrollToReference`, vs. computing a vertical offset. Resolve during implementation against `JEditorPane` behavior; both are viable on 2024.2.
- **Config-node preview:** whether selecting the config node previews `config.yaml` (fenced) or shows the empty state — minor; default to the empty state for MVP unless trivial.
