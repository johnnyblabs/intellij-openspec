## Why

The plugin should not introduce concepts the OpenSpec client does not have. The `@spec <domain>:<requirement>` **source-code annotation**, and the two surfaces built on it — the **Coverage panel** and the **`@spec` gutter markers** — are a plugin invention with no basis in OpenSpec:

- **`@spec` does not exist upstream.** Verified against `@fission-ai/openspec@1.4.1`: zero references to `@spec`, no CLI command, and no concept of annotating source code anywhere. The annotation and its vocabulary are pure plugin coinage that users must learn but OpenSpec never defines.
- **OpenSpec has exactly two kinds of thing, and neither links to code.** *Specs* (`openspec/specs/`) are the living source of truth — you `list`/`show`/`validate` them; they have no status, progress, or completion concept (the CLI refuses `openspec status` for a spec). *Changes* are work with a lifecycle — they have status (artifacts + tasks). "Progress/completion" is a property of changes, never of specs. There is no third "code references a spec" concept.
- **The Coverage panel commits a category error.** Rendering "N/172 requirements covered, X%" applies change-style completion semantics to specs — measuring the living source of truth as if it were an unfinished checklist. OpenSpec deliberately denies this: completeness is judged transiently, per-change, by the `verify-change` AI workflow (which *semantically searches the codebase*, requires no annotation, and stores no metric).

Removing the annotation removes both surfaces at once, since both consume the same `@spec` comment. There is no OpenSpec-native replacement to offer — code↔spec linking is simply not an OpenSpec concept.

**Release context (verified):** the feature is **Java-only** and shipped in v0.2.0, live in the current Marketplace stable **0.2.10**. The language-agnostic improvement (GitHub #18) is staged in the **unreleased** v0.3.0 and has never reached users. This change **supersedes** that fix (see below): no released behavior regresses, because the broader cross-IDE version never shipped, and the Java-only version being removed is the partially-broken niche surface.

## What Changes

- **Delete the Coverage panel surface.** Remove `SpecCoveragePanel`, the Coverage tab registration in `OpenSpecToolWindowFactory`, and `SpecCoverageService` + `model/CoverageResult`.
- **Delete the `@spec` gutter markers.** Remove `SpecRefLineMarkerProvider` and its `codeInsight.lineMarkerProvider` registration in `plugin.xml`.
- **Remove the `@spec` convention from docs** (8 files) — it is no longer a plugin concept. Where a completeness signal is wanted, point users at OpenSpec's own `verify-change` workflow.
- **Editor spec delta** removes the "Spec gutter markers" and "Spec coverage panel" requirements with no replacement.
- **Supersede `fix-coverage-language-detection`** — it polishes the feature being deleted and is unreleased; it will not be archived. GitHub #18 is resolved as "feature removed; here is the OpenSpec-scope reasoning."
- **Out of scope / kept:** `editor` syntax highlighting (renders OpenSpec's own files, no new concept) and the delta spec diff viewer (a view of OpenSpec's own change model) are NOT touched.

## Capabilities

### New Capabilities
<!-- none -->

### Removed Capabilities
- `editor`: the "Spec gutter markers" and "Spec coverage panel" requirements are removed. The `@spec` source-annotation concept is retired entirely; no navigation or coverage surface replaces it.

## Impact

- **Code (delete):** `toolwindow/SpecCoveragePanel.java`, `services/SpecCoverageService.java`, `editor/SpecRefLineMarkerProvider.java`, `model/CoverageResult.java`, `test/.../SpecCoverageServiceTest.java`, `test/.../SpecRefLineMarkerProviderTest.java`.
- **Code (edit):** `resources/META-INF/plugin.xml` (drop the `SpecRefLineMarkerProvider` line-marker registration), `toolwindow/OpenSpecToolWindowFactory.java:100-103` (drop the Coverage tab), `util/OpenSpecFileUtil.java:37` (drop the `@spec` comment reference). Verify no other references via `grep -rn "SpecCoverage\|SpecRefLineMarker\|@spec" src/`.
- **Specs:** `openspec/specs/editor/spec.md` (delta — REMOVE two requirements).
- **Docs:** `docs/getting-started-browser.md`, `docs/feature-reference.md`, `docs/marketplace-page.md`, `docs/feature-comparison-matrix.md`, `docs/getting-started-api.md`, `docs/getting-started-cli-companion.md`, `README.md`, `CONTRIBUTING.md` — remove `@spec`/coverage descriptions; `CHANGELOG.md` (unreleased v0.3.0): add a "Removed: `@spec` coverage panel and gutter markers (out of OpenSpec scope)" entry (no "#18 fixed" line exists on `main` to replace).
- **Supersedes:** change `fix-coverage-language-detection` (unreleased; abandoned, not archived). See design.md → "Supersession and git approach".
- **Public:** GitHub #18 — vendor-neutral reply explaining the removal rationale (no internal tracker references).
- **Tracker:** mirrored to internal trackers via the gitignored sidecar.