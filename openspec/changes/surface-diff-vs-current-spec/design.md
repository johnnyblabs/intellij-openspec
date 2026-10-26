# Design

## Context

- **The diff itself:** `DeltaSpecDiffAction(deltaSpecPath)` opens a `SimpleDiffRequest`: the current main spec (`specs/<domain>/spec.md`) on the left, the change's delta on the right. With no main spec, the left side is empty and titled "New capability — no existing spec".
- **Where it's offered today:** a delta spec node's right-click menu, and the Change Deltas preview's `openspec-diff:<capability>` links. Both build the path as `<changeDir>/specs/<capability>/spec.md`, and the tree lists capabilities with `ChangeService.getDeltaSpecNames(change)` (flat `specs/<name>/spec.md`).
- **The chip menu:** `WorkflowActionPanel.showChipContextMenu` gives a DONE chip "Open file", "Regenerate" and "Copy prompt".
- **The popover:** after a Direct API generation, the panel calls `showGuidancePopover(anchor, "✓ Generated …", "Saved to: …", null)`. Regenerate goes through the same generation path.
- **The specs artifact:** its `outputPath` is a glob under `specs/` (`specs/**/*.md` in spec-driven). Delta specs live under the change's `specs/` for any schema.

## Goals / Non-Goals

**Goals:**

- Reach the existing diff from where specs are generated, with wording that can't be read as history.

**Non-Goals:**

- Changing what the diff shows, including for a new capability.
- Before/after review of a regeneration, which is tracked separately.
- Nested capability folders, which the tree and the diff don't list today either.

## Decisions

### D1. One builder for the entries
- **The builder:** `DiffVsCurrentSpec.entries(artifact, changeDir, capabilities)` returns `(capability, deltaSpecPath)` pairs.
  - The artifact must be the specs artifact: DONE, with an output path under `specs/`.
  - Each capability comes from `getDeltaSpecNames`, with the path `<changeDir>/specs/<capability>/spec.md`, the same path the tree and the preview build.
  - Otherwise the list is empty.
- **Why it exists:** it's pure, so tests cover the cases without a panel.

### D2. The chip menu
- **The submenu:** when the entries aren't empty, the DONE chip's menu gets a separator and a **Diff vs current spec** submenu with one item per capability, labelled by capability.
- **The tooltip:** "What this change does to specs/<capability>/spec.md, compared with the current spec".
- **Launching:** each item launches `DeltaSpecDiffAction` through `ActionLauncher` from the chip.
- **Capabilities:** read with `ChangeService.getDeltaSpecNames` when the menu opens. It's a directory listing, cheap on the EDT, and the menu already reads state there.

### D3. The popover link
- **The link:** `showGuidancePopover` takes an optional extra link (text plus action). After a Direct API generation whose output path is under `specs/`, the panel passes **Diff vs current spec**.
- **What it opens:** one capability opens its diff directly; several open a platform list chooser, titled "Diff vs current spec", and the chosen one opens.
- **The capabilities** are read when the link is clicked, so they reflect the written files.
- **Clipboard and editor-tab delivery** get no link: the agent hasn't written the specs yet.

### D4. Wording
- **The names:** the submenu and link say "Diff vs current spec". The tooltip says "compared with the current spec".
- **Avoided:** nothing says "changed", "last", "before" or "previous". A test checks the strings.

## Risks / Trade-offs

- **Custom schemas:** one that writes specs somewhere other than `specs/` gets no entries, because D1's check is false. That matches where OpenSpec reads delta specs.
- **The chooser** is one more click with several capabilities. It's still one place to go instead of the Browse tab.
