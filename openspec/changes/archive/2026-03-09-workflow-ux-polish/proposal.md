# Workflow UX Polish

## What
Three targeted UX improvements to the workflow panel:
1. Auto-focus newly proposed changes in the WorkflowActionPanel
2. Transform Generate button to "Waiting for [file]..." during clipboard file watch
3. Make READY pipeline chips clickable to trigger generation

## Why
These refinements remove the last friction points for new users navigating the spec-driven lifecycle. Auto-focus eliminates the "where did my change go?" confusion. Waiting state provides visual feedback during the clipboard workflow. Clickable READY chips make the pipeline interactive — the most obvious "next step" becomes clickable.

## Scope
- `OpenSpecProposeAction`: After creating a change, auto-select it in the workflow panel
- `WorkflowActionPanel`: Update Generate button text during file watch, make READY chips trigger generation
