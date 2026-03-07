## Why

The current generate workflow has three UX problems: (1) the WorkflowActionPanel silently picks the first active change, so users with multiple changes don't know which one they're operating on, (2) the toolbar "Generate Artifact..." and "Generate All" actions duplicate the panel's functionality with a less guided experience, and (3) the artifact pipeline (proposal → design → specs → tasks) and its DAG dependencies are invisible — users see individual artifacts but not the progression.

## What Changes

- Add a change selector dropdown to the WorkflowActionPanel so users can explicitly pick which change they're working on when multiple active changes exist (auto-selects when only one)
- Show the artifact pipeline visually in the panel — a compact progress indicator showing each artifact's state (done/ready/blocked) so users can see where they are in the workflow
- Remove the toolbar "Generate Artifact..." and "Generate All Artifacts" actions — the WorkflowActionPanel already provides a better, guided version of both
- Update context menu on change nodes to route generation through the panel rather than standalone actions

## Capabilities

### New Capabilities
- `change-selector`: Change selector dropdown widget for the WorkflowActionPanel, handling single-change auto-selection and multi-change explicit selection

### Modified Capabilities
- `workflow-panel`: Add artifact pipeline visualization showing DAG state, and integrate change selector
- `tool-window`: Remove generate actions from toolbar, update context menus to use panel-driven generation
- `actions`: Remove GenerateArtifactAction and GenerateAllArtifactsAction from menu bar and toolbar registrations

## Impact

- `WorkflowActionPanel.java` — major changes: add change selector, add pipeline visualization
- `plugin.xml` — remove GenerateArtifact and GenerateAll action registrations from menu and toolbar groups
- `GenerateArtifactAction.java` — delete or reduce to thin delegate
- `GenerateAllArtifactsAction.java` — delete
- `SpecTreeModel.java` — update context menu for CHANGE nodes
- `OpenSpecToolWindowPanel.java` — update context menu to remove generate entries
