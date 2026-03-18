## Why

The WorkflowActionPanel's bottom half has accumulated 10 action buttons, an expanding guidance panel, progress bars, task labels, and a compliance chip — all managed through scattered visibility logic across 2200+ lines. The result is visual clutter that shifts layout unpredictably and hides the one thing users actually need: what to do next. The pipeline chips are already the best part of the UI — they should be the entire interface.

## What Changes

- Eliminate the horizontal action button row entirely (Generate, Generate All, Apply, Verify, Sync Specs, Archive, Bulk Archive, Start New, Retry, Cancel)
- Make pipeline chips the primary action surface: click READY chip to generate, click DONE chip to open file, right-click any chip for context menu (regenerate, copy prompt, open)
- Replace the expanding inline guidance panel with a tooltip/popover that appears on generation and auto-dismisses — no more layout shifts
- Add a compact icon action bar (3-4 small icons: FF, Archive, Verify, overflow menu) in the corner for secondary actions
- Consolidate the compliance chip, task progress, and delivery mode into a slim status strip below the pipeline
- Reduce the panel's maximum height from ~350px to ~100px in steady state

## Capabilities

### New Capabilities
- `pipeline-interaction`: Chip click/right-click actions, hover states, and visual affordances that make the pipeline the primary action surface
- `guidance-popover`: Tooltip-style guidance that appears after generation without expanding the panel layout

### Modified Capabilities
- `workflow`: Replace button-driven workflow panel with chip-driven interaction model and compact icon action bar

## Impact

- Affected code: `WorkflowActionPanel.java` (major rewrite of action row, guidance panel, and chip interaction), `OpenSpecToolWindowPanel.java` (split pane sizing)
- Affected UI: The entire bottom panel visual layout changes — existing button-based workflows replaced with chip-based interactions
- No API, service, or model changes — this is purely UI/presentation layer
