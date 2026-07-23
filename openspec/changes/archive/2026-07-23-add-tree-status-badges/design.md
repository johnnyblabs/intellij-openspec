## Context

The Browse tree (`SpecTreeCellRenderer` over `SpecTreeModel`) already resolves each change artifact's status into distinct node types (`ARTIFACT_DONE`/`ARTIFACT_READY`/`ARTIFACT_BLOCKED`, `MISSING_ARTIFACT`) at model-build time via `ArtifactOrchestration`/`ChangeArtifactDag` (CLI `openspec status`). It expresses that status three redundant ways — a foreground color, a font style, and a Unicode glyph glued to the label (`✓/○/−`) — but never as an icon overlay. This change replaces the glyph hack with a real corner badge on the node icon.

An advisory pass fixed the design's guardrails: **change artifacts and changes carry status** (apply-readiness, task progress — first-class client concepts); **specs and requirements carry none** (`list --specs --json` is `{id, requirementCount}`), so badging them would repeat the removed `@spec` scorecard. The platform path is a cached `LayeredIcon` corner overlay reusing existing `AllIcons`.

## Goals / Non-Goals

**Goals:**
- Overlay a status badge on the icon for the status-bearing nodes (change, change-artifact, missing-artifact), reusing the already-computed status.
- Retire the label glyph-prefix; add a change-node done rollup + task `X/Y` suffix.
- Keep specs/requirements plain (the hard on-model line), enforced by a test.
- Theme/HiDPI-correct, tooltip-named, zero per-paint cost.

**Non-Goals:**
- No badges on spec/requirement/delta-spec-file/config nodes.
- No custom SVGs, no new dependency, no new extension point.
- No animated `GENERATING`/`ERROR` badges (follow-up).
- No new status plumbing — the renderer maps existing precomputed status to icons.

## Decisions

**Decision 1 — `LayeredIcon` corner overlay, cached static, in `SpecTreeCellRenderer`.**
Compose `new LayeredIcon(2)`: layer 0 = the existing base icon, layer 1 = a small badge via `setIcon(badge, 1, SwingConstants.SOUTH_EAST)`. Build each `(base × status)` combination **once** into static constants; the renderer does a pure map/switch lookup — no `new LayeredIcon(...)` in `getTreeCellRendererComponent`. *Alternatives:* `IconManager.createLayered` (lower-level, no benefit), `ExecutionUtil.getLiveIndicator` (semantically the "running" indicator — wrong), hand-drawn `Graphics2D` (breaks HiDPI/theme). Avoid the deprecated `LayeredIcon(Icon...)` varargs constructor.

**Decision 2 — Reuse existing `AllIcons`; distinct shapes, not color-alone.**
Badges from stable platform icons (e.g. `RunConfigurations.TestState` dots or `Nodes.ErrorMark`/`WarningMark`) rather than shipping SVGs — SVG composition gives theme/dark/HiDPI correctness for free. Shapes differ across done/ready/blocked/missing so the states read in grayscale and to a screen reader (via tooltip). Final glyph selection is eyeballed once in `runIde` (Light/Dark, Classic/New UI).

**Decision 3 — Badge only the status-bearing node types; enforce with a test.**
`getIconForType` returns a layered icon for `ARTIFACT_DONE/READY/BLOCKED`, `MISSING_ARTIFACT`, and (rollup) `CHANGE` when apply-ready; a plain icon for everything else. A headless renderer unit test asserts `ARTIFACT_DONE → LayeredIcon` and `REQUIREMENT`/`SPEC_DOMAIN → plain icon` — the second assertion is the on-model guard against a future edit badging a spec node.

**Decision 4 — Change rollup from `isComplete()`; task `X/Y` as a label suffix.**
The change node gets a done badge when `ChangeArtifactDag.isComplete()` (CLI-emitted, on-model). Task progress `X/Y` (from the existing checkbox counter over `tasks.md`) is a **label suffix**, not a badge (badges can't render text). Both reflect client-owned state only.

**Decision 5 — Built-in fallback degrades honestly.**
`done` (file existence) and task counts are disk-derivable; `ready` vs `blocked` needs the CLI's schema DAG. With no CLI, collapse to a single not-done badge rather than hardcoding the DAG. The existing node-type mapping already reflects whatever the status source provided.

## Risks / Trade-offs

- **Per-paint cost** (renderer runs per visible row) → mitigated by static-cached `LayeredIcon` constants; no allocation or I/O at paint.
- **AllIcons constant drift across IDE versions** → low (chosen constants are long-stable), but `verifyPlugin` gates it so a `NoSuchFieldError` against 2024.2 is caught locally, not on CI.
- **Redundancy with the existing foreground color** → keep a subtle tint as reinforcement; the overlay is the signal. Avoid three-way redundancy by dropping the glyph prefix.
- **Overlay legibility at 16px** → use the platform's small-glyph icons (12px) in the SE corner; verify in the sandbox.

## Migration Plan

Additive rendering change; no user migration, no schema/dependency change. Steps: (1) add cached `LayeredIcon` constants + `getIconForType` routing in the renderer; (2) drop the glyph prefix from `SpecTreeModel.buildArtifactLabel`; (3) add the change done-rollup + task suffix; (4) extend tooltips for the rollup; (5) renderer unit test (incl. the spec-never-badged guard); (6) `verifyPlugin` + screenshot shot. Rollback is a straight revert.

## Open Questions

- **Exact badge glyphs** — `RunConfigurations.TestState` dots vs `Nodes.*Mark` glyphs for blocked/missing; decide by eyeballing in `runIde`. Both are 2024.2-stable.
- **Whether to keep the foreground color tint** once the badge exists — lean toward a subtle tint for done/blocked as reinforcement; finalize during implementation.
