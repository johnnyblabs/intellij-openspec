## Why

The Apply action currently just flips the `.openspec.yaml` status to "applied" — it provides no guidance on how to actually implement the change's tasks. Users who click Apply after generating artifacts see a notification and nothing else. For IDE panel tool users (Copilot, Cursor) there's no obvious path from "tasks are ready" to "tasks are implemented." Apply should deliver task context to the user's AI tool the same way Generate delivers artifact prompts.

## What Changes

- Rewrite `OpenSpecApplyAction` to assemble a full-context implementation prompt (design + specs + tasks) and deliver it via the existing tool selector delivery mechanism (clipboard, editor tab, or Direct API)
- Add an "Apply" button to `WorkflowActionPanel` that appears when all artifacts are complete and tasks remain, using the same delivery flow as Generate
- Watch `tasks.md` for checkbox changes after delivery to track implementation progress
- Show task progress in the workflow panel (e.g., "5/12 tasks complete")
- Add a soft hint when the task list is large (10+ remaining tasks)

## Capabilities

### New Capabilities
- `apply-task-delivery`: Delivery-aware Apply action that assembles implementation context and delivers it to the user's selected AI tool

### Modified Capabilities
- `workflow-panel`: Add Apply button visibility, task progress display, and post-apply watching state to the workflow panel
- `actions`: Update Apply action from status-flip to delivery-aware task prompt assembly

## Impact

- `src/main/java/com/johnnyb/openspec/actions/OpenSpecApplyAction.java` — rewrite to assemble and deliver task prompt
- `src/main/java/com/johnnyb/openspec/toolwindow/WorkflowActionPanel.java` — add Apply button, task progress indicator, post-apply watching state
- `src/main/java/com/johnnyb/openspec/model/ArtifactInstruction.java` — potentially add `buildApplyPrompt()` or similar method for task context assembly
- No new dependencies required — reuses existing delivery infrastructure (tool selector, clipboard, file watcher)
