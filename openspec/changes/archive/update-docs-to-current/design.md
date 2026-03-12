## Context

Recent changes (`toolbar-cleanup`, `panel-lifecycle-completion`, `workflow-ux-polish`) modified the plugin's toolbar, workflow panel, and validation behavior. Two docs files reference the old behavior and need updating.

## Goals

- Update `docs/feature-comparison-matrix.md` and `docs/getting-started-copilot.md` to reflect current plugin state
- Keep changes minimal — only fix what's actually wrong

## Non-Goals

- Rewrite or restructure the docs
- Add documentation for unreleased features
- Create new docs files

## Decisions

### 1. feature-comparison-matrix.md — Core Workflow table

**What's wrong:** Lines 25–26 say Apply and Archive are "menu action". They are now panel-only actions (Apply in workflow panel, Archive button appears when all tasks complete).

**Fix:** Change "Yes (menu action)" to "Yes (panel action)" for both Apply and Archive rows. Update the date on line 1 to 2026-03-11.

**Rationale:** These actions were removed from the toolbar in `toolbar-cleanup` and Archive was added to the panel in `panel-lifecycle-completion`.

### 2. feature-comparison-matrix.md — Validation table

**What's wrong:** Missing per-change validation and auto-validation at phase transitions.

**Fix:** Add a row for "Per-change validation" and "Auto-validation at phase transitions" in the Validation section.

### 3. getting-started-copilot.md — Step 5 pipeline complete text

**What's wrong:** Line 335 says `The Generate button shows: **"All complete"**`. Now the Generate button hides and an Archive button appears instead.

**Fix:** Replace with text describing the Archive button appearance and auto-validation message.

### 4. getting-started-copilot.md — Step 7 archive instructions

**What's wrong:** Lines 375–384 say "Go to **OpenSpec > Archive** in the menu bar". Archive is now triggered from the workflow panel's Archive button, which appears automatically when all tasks are complete.

**Fix:** Rewrite Step 7 to describe clicking the Archive button in the workflow panel. Keep the sync and archive directory explanation unchanged.

### 5. getting-started-copilot.md — Date update

**Fix:** Update "Last verified" date on line 3 to 2026-03-11.

## Risks / Trade-offs

- **Low risk:** These are documentation-only changes with no code impact.
- The feature-comparison-matrix could drift again with future changes — but that's inherent to docs and not worth automating at this stage.
