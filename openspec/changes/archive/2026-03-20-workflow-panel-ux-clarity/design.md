## Context

The workflow panel (`WorkflowActionPanel`) displays a pipeline card for the active change with chips, an icon bar, and a status strip. Currently the panel maintains its own change selection via a dropdown, completely independent of the tree view. The icon bar mixes change-scoped actions (Verify, Archive) with a creation action (Fast-Forward) and the overflow menu mixes single-change actions (Sync Specs, Apply Tasks) with an all-changes action (Bulk Archive). Users cannot tell which change an icon targets or why an icon is disabled.

## Goals / Non-Goals

**Goals:**
- Make it obvious which change every action targets
- Sync tree selection with the workflow panel so they feel like one UI
- Separate creation actions from change-specific actions
- Clarify scope of all overflow menu items (one change vs all changes)
- Explain disabled states via tooltips

**Non-Goals:**
- Redesigning the pipeline chips or status strip (those are fine)
- Adding new workflow capabilities
- Changing the underlying action logic — only presentation and wiring

## Decisions

### Tree-to-panel synchronization via TreeSelectionListener

Add a `TreeSelectionListener` to `OpenSpecToolWindowPanel` that detects when a change node (or a child of a change node) is selected in the tree. When detected, call `WorkflowActionPanel.setActiveChange(changeName)` to update the panel. Keep the dropdown as a fallback for direct selection. The sync is one-way: tree drives panel, not the reverse.

### Relocate Fast-Forward to the "no changes" card and overflow menu

FF creates a new change — it's not an action on the current change. Remove it from the icon bar. It already exists on the "No changes yet" card. Add it to the overflow menu as "New Change (Fast-Forward)..." grouped with "Start New Change" under a separator labeled "— Create —".

### Icon bar shows only change-scoped actions

The icon bar retains: Verify, Archive, and the overflow menu (⋯). These all operate on the currently selected change. This makes the icon bar semantics clear: everything here is about THIS change.

### Overflow menu groups by scope

Structure the overflow menu with visual separators:

```
Apply Tasks
Sync Specs
Compliance Check
─────────────────
Archive All Changes...
─────────────────
Start New Change...
Fast-Forward...
```

The first group is change-scoped (operates on active change). "Archive All Changes..." is clearly labeled as all-changes scope. The bottom group is creation actions.

### Contextual tooltips on all icon bar buttons

Every icon bar button gets a tooltip that includes:
- **Enabled**: action description + target change name — e.g., "Verify: change-name"
- **Disabled**: reason — e.g., "Verify (complete all artifacts first)"
- **Archive disabled**: "Archive (complete all artifacts and tasks first)"

### Change name badge on the icon bar

Add a small label at the left edge of the icon bar showing the active change name in a muted style (e.g., `change-name ·`). This anchors the icon bar to a specific change without requiring the user to look up at the dropdown.

## Risks / Trade-offs

- **Risk: Tree-panel sync could be jarring** — Mitigate by only syncing when the user explicitly clicks a change node, not on programmatic tree updates.
- **Risk: Removing FF from icon bar reduces discoverability** — Mitigate by keeping it in the overflow menu and on the no-changes card. FF is a less frequent action than Verify/Archive.
- **Trade-off: Overflow menu gets longer** — Accept this. Grouping with separators keeps it scannable, and the menu only appears on click.
