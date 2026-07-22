## 1. Preview pane — master/detail in the Browse tab

- [x] 1.1 In `OpenSpecToolWindowPanel`, wrap the current center in the left component of a horizontal `com.intellij.ui.OnePixelSplitter`; add a read-only `JEditorPane` preview as the right component, using `new HTMLEditorKitBuilder().build()` (not the raw `HTMLEditorKit`). Persist the splitter proportion by key; make the right pane collapsible.
- [x] 1.2 Add a debounced `TreeSelectionListener`: on selection, hand off to `ApplicationManager.getApplication().executeOnPooledThread(...)`, read the node's file via `VfsUtilCore.loadText` (re-resolve the path string through `LocalFileSystem` — do NOT cache `VirtualFile` across model rebuilds), render with `MarkdownHtmlRenderer`, then `invokeLater` to `setText` + `setCaretPosition(0)`. No `ReadAction` (plain text, not PSI).
- [x] 1.3 Show a placeholder empty state when nothing previewable is selected. Preserve existing double-click-opens-file behavior unchanged.

## 2. Per-node-type rendering + delta badges + anchoring (extract as PURE, testable classes)

- [x] 2.1 `SpecPreviewRenderer.renderNode(...)`: classify the selected node (main spec / change artifact proposal|design|tasks / delta spec) by node type + path, then render the source markdown — pure of the file read (take the markdown string + node type as input; the caller does the VFS read). Never route a main spec and a delta spec through the same interpretation; a non-previewable node returns the empty-state placeholder marker.
- [x] 2.2 `DeltaBadgeDecorator.decorate(html)`: pure string→string post-process over rendered HTML that badges the `ADDED`/`MODIFIED`/`REMOVED`/`RENAMED` operation headers with a stable themed marker class. Idempotent; a plain `## Requirements` main-spec heading gets NO badge.
- [x] 2.3 `RequirementAnchors`: pure `anchorId(name)` (stable slug) + `injectAnchors(html)` (adds `id`/`<a name>` to requirement headings). The selection handler calls `scrollToReference(anchorId(name))`; keep the falsifiable part (slug/injection round-trip) pure.

## 3. Full-text search (tree-view modification) — pure matcher

- [x] 3.1 `SpecContentMatcher.matches(SpecFile/Requirement, query)`: pure, case-insensitive match over name + `getBody()` + scenario name/clauses. Thread a `searchText` onto `TreeNodeData` (or a testable `buildSpecsNode(List<SpecFile>, query)` overload) so `SpecTreeModel.filterNode` matches label OR content. Match on the fly during the off-EDT model build over the bounded `openspec/` corpus — no persisted index; preserve real-time + auto-expand + case-insensitive behavior.
- [ ] 3.2 (Optional) `TreeSpeedSearch.installOn(tree)` for keyboard nav — the static factory, not the deprecated constructor / `Convertor` overloads.

## 4. Tests — pure-unit first, one platform read, uiSmoke for wiring

NOTE (per test-engineer): the MVP parses **no** new CLI JSON — the preview renders files directly and content-search reads the plugin's own `SpecFile` model, whose CLI fidelity is ALREADY covered by `SpecParserCliStructureContractTest` against the existing `fixtures/cli/1.6.0/spec-structure/*.show.json`. So **capture no new fixtures.** Assert on structural HTML markers, never exact markup; assert both directions (match present AND non-match absent).

- [x] 4.1 `SpecContentFilterTest` (plain JUnit): a term in a requirement body/scenario only (no label match) surfaces its spec + requirement nodes; a non-matching term prunes them; case-insensitive; label-only match still works (no regression).
- [x] 4.2 `DeltaBadgeDecoratorTest` (plain JUnit): each of ADDED/MODIFIED/REMOVED/RENAMED gets a badge marker; a plain `## Requirements` heading does NOT (the never-conflate guard); decorating twice does not double-badge.
- [x] 4.3 `RequirementAnchorTest` (plain JUnit): `anchorId` stable + collision-free across distinct names; `injectAnchors(html)` output contains an anchor equal to `anchorId(name)` (round-trip invariant, not a hardcoded slug).
- [x] 4.4 `SpecPreviewRenderTest` (plain JUnit): main-spec markdown → requirement heading present, no badge; delta markdown → badge markers; same source routed as delta vs main yields different output; non-previewable node → placeholder marker. Assert on the render fragment (before `wrapInHtml`) to avoid the theme-CSS/`JBUI` headless dependency.
- [x] 4.5 `SpecPreviewFileReadTest` (`BasePlatformTestCase`): create a real spec via `myFixture`, resolve its `VirtualFile`, run the read+render path, assert HTML contains a known heading — covers the "re-resolve path, don't cache VirtualFile" file-read contract. Do NOT drive the listener/Alarm/invokeLater hop here.
- [x] 4.6 Release-gated uiSmoke Journey (never per-PR): open tool window → Browse → select a known spec node (via the platform tree API/seam, NOT a `byVisibleText` row click — tree rows are renderer paint) → assert the preview pane renders the expected requirement name via `hasText`/`hasSubtext`. This is the only tier that catches the selection→render EDT wiring.
- [ ] 4.7 (Optional) Strengthen `SpecParserCliStructureContractTest` to also assert requirement/scenario TEXT parity against the `text`/`rawText` fields already in the `spec-structure/*.show.json` fixtures — using normalized containment (CLI `text` appears in the parser body), NOT `assertEquals`. Skip if it can't be made non-brittle.

## 5. Docs, demo, build

- [x] 5.1 Add ONE screenshot-tour shot of the master/detail (tree left + rendered spec right) — the headline v0.5.0 visual — via the existing screenshot script.
- [x] 5.2 Update `docs/feature-reference.md` (and the wiki tool-window page) to describe the preview pane, per-node rendering, delta badges, requirement anchoring, and full-text search. Vendor-neutral.
- [x] 5.3 Update `README.md` / `CHANGELOG.md` `## Unreleased` with the user-facing feature (searchable spec/change viewer with rendered preview). No tracker identifiers.
- [x] 5.4 Run `./gradlew build` green (suite + coverage floor). Confirm `verifyPlugin` is not required (recommended stack adds no new `com.intellij.*` risk surface, no new dependency); if the design ever swaps in `JBHtmlPane`/JCEF, run `verifyPlugin`.
