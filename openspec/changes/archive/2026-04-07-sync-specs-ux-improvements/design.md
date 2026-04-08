## Context

The icon action bar in `WorkflowActionPanel` currently renders five buttons: Apply, Compliance, Verify, Archive, and an overflow menu (⋯). Sync Specs lives inside the overflow menu and is enabled only when `hasDeltaSpecs` is true. The `onArchive()` method has no check for unsynced delta specs — it proceeds directly to `changeService.archiveChange()`, which moves the change directory to the archive. Any unsynced delta specs are silently archived without being merged into main specs.

The icon bar is constructed at lines 209–226 of `WorkflowActionPanel.java`. Buttons are added to a `FlowLayout(RIGHT)` panel in order: Apply → Compliance → Verify → Archive → Overflow. The `hasDeltaSpecs` volatile field is already computed asynchronously during `refresh()` and is available for enabling/disabling the new button.

## Goals / Non-Goals

**Goals:**
- Make Sync Specs a visible, first-class action in the icon bar
- Prevent accidental archiving of changes with unsynced delta specs
- Maintain the established icon bar pattern (small icon buttons with contextual tooltips)

**Non-Goals:**
- Automatic sync-before-archive (user must explicitly choose)
- Refactoring `WorkflowActionPanel` into smaller classes (separate change if pursued)
- Changes to the Sync Specs execution logic itself (`onSyncSpecs()` is unchanged)
- Changes to the overflow menu beyond removing Sync Specs from it

## Decisions

### 1. Sync Specs icon positioned between Verify and Archive

The icon bar order becomes: Apply → Compliance → Verify → **Sync Specs** → Archive → Overflow.

This mirrors the natural workflow: verify correctness, sync specs to main, then archive. Placing it adjacent to Archive makes the relationship visible.

**Alternative considered:** Placing it in a sub-menu or keeping it in overflow with just an archive guard. Rejected because the action is important enough to warrant a dedicated button — it's the only way to persist spec changes — and hiding it contributed to the current problem.

### 2. Use `AllIcons.Actions.Download` for the Sync Specs icon

This icon conveys "merge/pull into" semantics. It's visually distinct from the existing icons (Execute, Analysis, Preview, Checked, More) and available in the IntelliJ icon set without custom assets.

**Alternative considered:** `AllIcons.Actions.Refresh` — rejected because it implies re-reading, not merging. `AllIcons.Vcs.Merge` — considered but may not be available in all SDK versions we target.

### 3. Archive guard uses a three-option dialog

When `hasDeltaSpecs` is true and the user clicks Archive, show `Messages.showYesNoCancelDialog()`:
- **"Sync First"** (Yes) — triggers `onSyncSpecs()`, does NOT auto-archive after (user re-clicks Archive after reviewing the sync diff)
- **"Archive Without Syncing"** (No) — proceeds with archive, delta specs are archived as-is
- **"Cancel"** — aborts, no action taken

This is inserted at the top of `onArchive()`, before the existing progress task. The check is a simple `if (hasDeltaSpecs)` guard on the EDT — no background work needed.

**Alternative considered:** Auto-syncing then auto-archiving in sequence. Rejected because sync shows a diff preview that the user needs to confirm — chaining them would bypass that review step.

### 4. Sync Specs button enabled only when `hasDeltaSpecs` is true

The button follows the same pattern as Archive: always visible, conditionally enabled. Tooltip shows "Sync Specs: \<change-name\>" when enabled, "Sync Specs (no delta specs)" when disabled. This keeps the icon bar stable (no buttons appearing/disappearing) and consistent with existing tooltip conventions.

### 5. Overflow menu retains only Cancel Generation

With Sync Specs promoted to the icon bar, the overflow menu contains only "Cancel Generation" (shown during generation). If no generation is in progress, the overflow button still appears but the menu will be empty of actionable items. This is acceptable — the overflow exists for conditional/rare actions.

## Risks / Trade-offs

- **Icon bar width**: Adding a sixth icon button may crowd the bar on narrow tool windows. → Mitigation: The `FlowLayout(RIGHT)` will wrap gracefully, and the icon is small (16×16). The change label on the left truncates naturally via `BorderLayout`.
- **User habit**: Existing users accustomed to finding Sync Specs in the overflow menu will need to discover the new location. → Mitigation: The icon is always visible and has a tooltip. The overflow menu no longer shows it, so there's no ambiguity.
- **Dialog fatigue**: The archive guard adds a click for users who intentionally archive without syncing. → Mitigation: The dialog only appears when `hasDeltaSpecs` is true. Changes without delta specs archive with zero extra friction.