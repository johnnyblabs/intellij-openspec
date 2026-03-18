## Context

The WorkflowActionPanel currently has three layers of UI stacked vertically: pipeline chips (good), an action button row with 10 buttons (cluttered), and an expanding guidance panel (unpredictable height). The button row is the core problem — most buttons are hidden via `setVisible(false)` but the layout still shifts as different buttons appear. The guidance panel compounds this by expanding to ~5 rows when showing post-generation feedback, then collapsing when dismissed.

The pipeline chips are already interactive (click READY to generate, click DONE to open file) but this isn't visually obvious — there's no hover effect, no cursor change on blocked chips, and the separate Generate button duplicates the chip's click behavior. Users don't discover the chip interaction because the button is right there.

**Current panel height:** 60px (empty) → 250px (FF input) → 350px (generating with guidance). Target: ~80-100px steady state.

## Goals / Non-Goals

**Goals:**
- Make the pipeline chips the obvious, primary action surface with clear visual affordances
- Eliminate the action button row entirely — move all actions to chip clicks, icon bar, or overflow menu
- Replace the expanding guidance panel with a non-layout-shifting popover
- Reduce steady-state panel height to ~100px
- Maintain all existing functionality — no features removed, just relocated

**Non-Goals:**
- Not changing the CardLayout structure (NO_CHANGES / FF_INPUT / PIPELINE cards remain)
- Not changing the header row (change selector + tool selector stay)
- Not redesigning the FF input form
- Not changing services, models, or business logic — purely UI/presentation

## Decisions

### 1. Pipeline chips become the sole generation trigger

**Decision:** Remove the `generateButton` and `generateAllButton`. Clicking a READY chip triggers generation for that artifact using the current delivery method. If Direct API is configured and multiple artifacts are ready, show a "Generate remaining" option in the right-click context menu (replaces Generate All button).

**Rationale:** The Generate button already duplicates the READY chip's click behavior. Removing it eliminates the "two ways to do the same thing" confusion. Generate All becomes a context menu option because it's a power-user action, not the default flow.

**Visual affordances for chips:**
- READY: Blue fill, hand cursor, subtle scale-up on hover (1.05x), tooltip "Click to generate"
- DONE: Green fill, hand cursor, tooltip "Click to open · Right-click for options"
- BLOCKED: Gray, default cursor, tooltip "Waiting on: [dependency names]"
- GENERATING: Pulsing border + spinner (existing), no click

**Alternatives considered:**
- *Keep Generate button alongside clickable chips*: Rejected — defeats the purpose of simplification. Two triggers for the same action is worse than one clear one.
- *Make chips drag-and-drop reorderable*: Over-engineered — the DAG order is fixed.

### 2. Compact icon action bar replaces button row

**Decision:** Replace the 10-button FlowLayout row with a compact icon strip containing 3-4 small `ActionButton`-style icons, right-aligned below the pipeline:

| Icon | Action | Visible when |
|------|--------|-------------|
| ⚡ (lightning) | Fast-Forward (activates FF input card) | Always (no active change or has changes) |
| ✓ (checkmark) | Verify | Artifacts complete |
| 📦 (archive) | Archive | All artifacts done |
| ⋯ (overflow) | Menu: Sync Specs, Bulk Archive, Apply Tasks, Compliance Check | Always |

Use IntelliJ's `ActionButton` component (same as tool window title bar buttons) for consistent look. Icons are 16x16, gray when unavailable, themed when active.

**Rationale:** These actions are secondary to the main generate→review→generate flow. They don't need prominent buttons — they need to be findable. An icon strip is the IntelliJ-native pattern for this (see any tool window's title bar).

**Alternatives considered:**
- *Vertical icon gutter on the left*: Considered but fights IntelliJ's existing sidebar pattern. The left edge belongs to the tree's gutter.
- *Toolbar at the top of the panel*: Competes visually with the change selector header row. Below the pipeline is more natural — actions flow from status.

### 3. Guidance becomes a popover, not an expanding panel

**Decision:** Replace `guidancePanel` (BoxLayout with 3 JTextAreas + 2 buttons) with a `JBPopupFactory` balloon or lightweight popup that appears near the chip that was just generated. The popover shows: (1) delivery confirmation ("Copied to clipboard"), (2) save-path hint, (3) "Copy again" link. Auto-dismisses after 8 seconds or on click-away. Does not affect panel layout.

**Rationale:** The guidance panel is the biggest source of layout instability. It expands the panel by 100-150px, pushes buttons down, and creates a jarring visual shift. A popover delivers the same information without moving anything.

**Content for the popover:**
```
✓ Copied to clipboard
Save to: openspec/changes/<name>/design.md
[Copy again]
```

**Alternatives considered:**
- *IntelliJ notification balloon*: Too far from the action (appears in corner). The guidance needs to be near the chip.
- *Tooltip on the chip*: Tooltips can't have clickable "Copy again" links. A lightweight popup can.
- *Keep the panel but make it collapsible*: Still shifts layout when opened. The fundamental problem is expansion, not visibility.

### 4. Status strip consolidates metadata

**Decision:** Below the pipeline chips, add a single-line status strip that shows (left to right): compliance status, task progress (if tasks exist), and delivery mode indicator. This replaces the scattered `complianceChip`, `taskProgressLabel`, `taskHintLabel`, and `elapsedTimeLabel`.

```
✓ Compliant · 3/5 tasks · Clipboard: Claude Code
```

**Rationale:** These are all status indicators, not actions. They belong in a status bar, not scattered between action buttons. One line, always visible, consistent position.

**During Generate All:** The status strip shows a compact progress indicator:
```
Generating 2/4... 1m 23s · Direct API
```

**Alternatives considered:**
- *Keep them as separate labels*: Current approach — scattered, inconsistent visibility, takes multiple rows.
- *Move to tool window status bar (bottom)*: Too far from the pipeline context.

### 5. Right-click context menu on all chips

**Decision:** Every pipeline chip gets a right-click context menu with state-appropriate actions:

| Chip state | Context menu items |
|------------|-------------------|
| DONE | Open file, Regenerate, Copy prompt |
| READY | Generate, Copy prompt |
| BLOCKED | (no menu — grayed out, tooltip shows dependencies) |
| GENERATING | Cancel |

**Rationale:** Right-click menus are the IntelliJ-native discovery mechanism. Users who don't know about click-to-generate will find it here. Power actions like "Regenerate" and "Copy prompt" that don't deserve a visible button get a natural home.

## Risks / Trade-offs

**[Discoverability of chip clicks]** → New users may not realize chips are clickable. **Mitigation:** READY chips use hand cursor, blue fill with hover scale effect, and tooltip "Click to generate." The no-changes card's "Getting Started" text will mention clicking chips.

**[Loss of visible Generate All button]** → Power users who relied on the prominent button may miss it in the context menu. **Mitigation:** The overflow menu (⋯) includes "Generate All Remaining" as the first item when multiple artifacts are ready.

**[Popover guidance may be missed]** → If the popover auto-dismisses and the user wasn't looking, they lose the save-path hint. **Mitigation:** The popover stays for 8 seconds (long enough to read). "Copy again" is also in the chip's right-click menu. The tool selector label shows the current delivery mode at all times.

**[Large refactor of WorkflowActionPanel]** → Significant rewrite of the UI code (~800 lines of button/guidance logic replaced). **Mitigation:** The services, models, and business logic are untouched. The refactor is purely presentation — existing tests for services still pass. New tests focus on chip interaction and state transitions.
