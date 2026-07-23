## Why

The Browse tree already knows each change artifact's status — it maps done/ready/blocked/missing to distinct node types from `openspec status` — but it *shows* that status by gluing a Unicode glyph (`✓ proposal`, `○ design`, `− specs`) onto the label text. That's a hack: it clutters the label, breaks on truncation, and reads poorly. A small **status badge overlaid on the node icon** turns the tree into a scan-in-one-glance view of every change's apply-readiness. This is on-model precisely because **change artifacts carry status** (it drives apply-readiness) — while **specs and requirements do not**, so they must stay plain (badging them would repeat the removed `@spec` coverage scorecard).

## What Changes

- Render artifact status as a **`LayeredIcon` corner badge** on the node icon — **done / ready / blocked / missing** — for change-artifact and missing-artifact nodes, replacing the glyph-prefix hack (labels return to plain `proposal` / `design` / `specs`).
- Add a **change-node rollup**: a done badge when all the change's artifacts are complete (apply-ready), plus the change's **task progress `X/Y`** as a label suffix.
- Badges use **distinct shapes** (not color alone) and reuse existing platform icons; **tooltips name the status** ("Complete", "Ready to generate", "Blocked by: …").
- **On-model boundary (enforced):** only **change**, **change-artifact**, and **missing-artifact** nodes may carry a status badge. **Spec, requirement, delta-spec-file, and config nodes never do.**
- Built-in (no-CLI) mode degrades ready/blocked to a single "not-done" badge (the ready-vs-blocked split needs the CLI's schema graph); done vs not-done and task progress remain available from disk.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `tree-view`: Adds a requirement for artifact/change **status badge overlays** on node icons (which nodes carry them, the state vocabulary, source, and the hard on-model line that spec/requirement nodes never do), and retires the label glyph-prefix in favor of the icon overlay.

## Impact

- **Affected code:** `SpecTreeCellRenderer` (compose the node icon as a cached `LayeredIcon` base + corner badge for the status-bearing node types), `SpecTreeModel` (drop the `toIcon()` glyph prefix from artifact labels; add the `X/Y` task suffix on change nodes), reusing the existing `ArtifactOrchestration`/`ChangeArtifactDag` status already mapped to node types and the existing tooltips. Badges use existing `AllIcons` — **no custom SVGs, no new dependency.**
- **Platform:** `com.intellij.ui.LayeredIcon(int)` + `setIcon(badge, layer, SwingConstants.SOUTH_EAST)`, cached as static constants (zero per-paint allocation); theme/HiDPI-correct via SVG composition. Present and non-deprecated on 2024.2; the deprecated `LayeredIcon(Icon...)` varargs is avoided. Adds new `com.intellij.ui.*`/`AllIcons.*` references, so **`verifyPlugin` gates this change** (the pre-push hook fires automatically).
- **On-model:** status is CLI-owned (`status --change` / `list --changes`); the renderer maps precomputed status → icon with no paint-time I/O. No spec/requirement status; no invented state.
- **User-visible:** artifact/change nodes gain a corner status badge and cleaner labels; spec/requirement nodes are unchanged.
- **Follow-ups (out of scope):** animated `GENERATING` / `ERROR` badges.
- **Tracker:** linked to an existing tracker issue via the gitignored `.tracking.yaml` sidecar.
