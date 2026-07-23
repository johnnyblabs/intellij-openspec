## 1. Badge icons + cached LayeredIcon composition

- [x] 1.1 Choose badge icons from existing `AllIcons` (no custom SVGs) for done / ready / blocked / missing — distinct **shapes**, not color-alone (e.g. `RunConfigurations.TestState.*` dots or `Nodes.ErrorMark`/`WarningMark`). Eyeball once in `runIde` (Light/Dark, Classic/New UI).
- [x] 1.2 In `SpecTreeCellRenderer`, add **static** `LayeredIcon` constants for each `(base icon × status)` combination: `new LayeredIcon(2)`, `setIcon(base, 0)`, `setIcon(badge, 1, SwingConstants.SOUTH_EAST)`. Do NOT use the deprecated `LayeredIcon(Icon...)` varargs. No `new LayeredIcon(...)` inside `getTreeCellRendererComponent` (zero per-paint allocation).

## 2. Route badges to the status-bearing nodes only

- [x] 2.1 `getIconForType` returns the cached layered icon for `ARTIFACT_DONE` / `ARTIFACT_READY` / `ARTIFACT_BLOCKED` / `MISSING_ARTIFACT`, and a **plain** icon for everything else. Never badge `SPEC_DOMAIN`, `REQUIREMENT`, `DELTA_SPEC`, `CONFIG`, `CONFIG_ENTRY`, `ARCHIVE`, `HINT`.
- [x] 2.2 Change-node rollup: badge the `CHANGE` node with the done badge when `ChangeArtifactDag.isComplete()` (apply-ready); otherwise no badge.

## 3. Retire the glyph hack + add task progress

- [x] 3.1 Drop the `ArtifactStatus.toIcon()` glyph prefix from `SpecTreeModel.buildArtifactLabel` — labels return to plain `proposal` / `design` / `specs`. Keep a subtle foreground tint as reinforcement.
- [x] 3.2 Add the change's task progress `X/Y` as a **label suffix** on the `CHANGE` node (reuse the existing checkbox counter), only when a tasks artifact exists.

## 4. Tooltips

- [x] 4.1 Ensure each badged node's tooltip names its status (extend the existing artifact tooltip for the change rollup: "Apply-ready — all artifacts complete", and the `X/Y tasks` phrasing). Vendor-neutral.

## 5. Tests

- [x] 5.1 Headless renderer unit test: `getIconForType(ARTIFACT_DONE)` (and READY/BLOCKED/MISSING) returns a `LayeredIcon`; `getIconForType(REQUIREMENT)` and `getIconForType(SPEC_DOMAIN)` return a **plain** (non-layered) icon — the second assertion is the **on-model guard** (a spec node must never be badged). Assert both directions.
- [x] 5.2 Test the change rollup: an apply-ready change (`isComplete()` true) routes the `CHANGE` node to the done badge; a non-complete change does not. And the task-suffix label formats `X/Y` correctly (and is absent when no tasks artifact).
- [x] 5.3 Keep/adjust any existing `SpecTreeModel`/renderer tests affected by dropping the glyph prefix.

## 6. Docs, demo, build

- [x] 6.1 Add a screenshot-tour shot of an expanded change with mixed artifact badges (the visual). Wire it into the tour + capture script; PNG capture is a manual `./gradlew screenshotTour` follow-up.
- [x] 6.2 Update `docs/feature-reference.md` (and the wiki tool-window page) to describe the status badges + the on-model boundary (only change/artifact nodes; specs never). Vendor-neutral.
- [x] 6.3 Update `CHANGELOG.md` `## Unreleased` (user-facing, vendor-neutral: change-artifact status now shows as icon badges + change-node apply-ready badge and task count). No tracker identifiers.
- [x] 6.4 Run `./gradlew build` green (suite + coverage floor; ratchet WITH MARGIN if coverage rose — do not ratchet to the measured max, per the 2026-07-23 floor note). Run **`./gradlew verifyPlugin`** — this change adds new `com.intellij.ui.LayeredIcon`/`AllIcons.*` references, so the verifier must confirm they resolve on 2024.2 (the pre-push hook fires it automatically).
