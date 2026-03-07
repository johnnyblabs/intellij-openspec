## Context

The WorkflowActionPanel currently sits below the tree in the tool window and provides a one-click Generate button that walks through the artifact DAG. Separately, the toolbar and menu bar have "Generate Artifact..." and "Generate All Artifacts" actions that duplicate this functionality with a less guided experience. The panel silently picks `active.get(0)` when multiple changes exist, making it unclear which change the user is operating on.

The artifact pipeline (proposal → design → specs → tasks) is defined by the CLI's DAG but only visible through individual artifact status icons in the tree — there is no compact summary of where you are in the workflow.

## Goals / Non-Goals

**Goals:**
- Users always know which change they're generating for
- The artifact pipeline progression is visible at a glance
- One clear path for generation (the panel), not three competing paths
- Minimal UI changes — enhance the existing panel, don't rebuild it

**Non-Goals:**
- Changing the underlying generation/delivery logic (clipboard, editor, direct API)
- Adding new delivery methods
- Changing how the tree displays artifacts (keep the detailed tree view)
- Modifying the propose/apply/archive workflow

## Decisions

### 1. Change selector: ComboBox in the panel header

Add a `ComboBoxAction`-style dropdown to the WorkflowActionPanel's info area. When only one active change exists, display it as a label (no dropdown chrome). When multiple exist, show a dropdown. This replaces the silent `active.get(0)` selection.

**Alternative considered:** Putting the change selector in the toolbar. Rejected because the toolbar is shared context and the change selector is specific to the generation workflow — keeping it in the panel keeps the concept cohesive.

### 2. Artifact pipeline: inline status chips

Add a row of compact status indicators between the change name and the Generate button. Each artifact is shown as a short label with a colored dot or icon (green check for done, blue circle for ready, gray for blocked). Example: `✓ proposal  ✓ design  ● specs  ○ tasks`

This gives at-a-glance pipeline visibility without consuming much vertical space.

**Alternative considered:** A full progress bar. Rejected because the DAG has branches (design and specs are parallel) — a linear bar misrepresents the topology. Status chips per artifact are more accurate.

### 3. Remove standalone generate actions

Delete `GenerateArtifactAction` and `GenerateAllArtifactsAction` from the menu bar and toolbar. The WorkflowActionPanel already provides a better UX. The context menu on change tree nodes will trigger the panel's generate via the existing `DataProvider` mechanism.

**Alternative considered:** Keeping them as "power user" alternatives. Rejected because maintaining two code paths for the same function doubles the bug surface (the `dependencies` parsing bug affected both paths) and confuses users about which is the "real" way to generate.

### 4. Context menu routes through panel selection

When a user right-clicks a change node and selects "Generate...", set the panel's active change to that node's change and trigger generation. This replaces the standalone action invocation.

## Risks / Trade-offs

- [Risk] Removing menu actions reduces discoverability for users who haven't found the tool window → The tool window is the primary interface and opens by default. The Propose action in the menu bar creates a change and the panel immediately shows the Generate button.
- [Risk] ComboBox adds visual weight to the panel when only one change exists → Mitigated by showing a plain label (no dropdown) for single-change case.
- [Trade-off] Removing "Generate All" means no batch generation from the menu → The panel's auto-advance already walks through artifacts one at a time, which is safer and provides feedback. Direct API users get auto-advance; clipboard users step through manually. Both are better than blind batch generation.
