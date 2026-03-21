## Why

The workflow panel's icon bar (Fast-Forward, Verify, Archive, overflow menu) is disconnected from the tree selection and mixes change-scoped actions with global actions, creating confusion about what each icon will do and which change it will affect. Users cannot tell at a glance whether an action targets the selected change, all changes, or creates something new.

## What Changes

- Sync tree selection with the workflow panel — clicking a change in the tree updates the panel's active change
- Separate change-scoped actions from global/creation actions in the icon bar
- Relocate Fast-Forward out of the change-specific icon bar (it creates a new change, not an action on the current one)
- Rename "Bulk Archive" to "Archive All Changes..." to clarify scope
- Add contextual tooltips showing the target change name and disabled-state reasons
- Visually distinguish single-change vs all-changes actions in the overflow menu

## Capabilities

### New Capabilities

_(none — this is a UX refinement of existing capabilities)_

### Modified Capabilities

- `pipeline-interaction`: Icon bar layout changes — separate change-scoped from global actions, relocate FF, add contextual tooltips
- `workflow`: Tree-to-panel synchronization — selecting a change in the tree updates the workflow panel
- `tree-view`: Tree selection events drive workflow panel state

## Impact

- `WorkflowActionPanel.java` — icon bar restructure, tooltip content, overflow menu labeling
- `OpenSpecToolWindowPanel.java` — add TreeSelectionListener to sync tree selection with workflow panel
- `SpecTreeModel.java` — may need helper to resolve selected change name from tree node
- No API changes, no new dependencies, no breaking changes
