# Design: apply-task-delivery

## Context

The Apply action currently exists as a simple status-flip: it updates `.openspec.yaml` from "proposed" to "applied" and shows a notification. Meanwhile, the Generate flow has a rich delivery-aware UX — tool selector, prompt assembly, clipboard/editor/API delivery, file watching, and pipeline advancement. Apply needs to reuse this same infrastructure to deliver task implementation context to the user's AI tool.

The plugin already has all the building blocks: `WorkflowActionPanel` with its tool selector and delivery pipeline, `ArtifactInstruction.buildPrompt()` for prompt assembly, `ArtifactFileWatcher` for detecting external changes, and the clipboard/editor-tab/API delivery modes.

## Goals / Non-Goals

**Goals:**
- Make Apply deliver a full-context implementation prompt (design + specs + tasks) via the existing tool selector
- Reuse the existing delivery infrastructure — no new delivery mechanisms
- Show task progress in the workflow panel after all artifacts are generated
- Watch `tasks.md` for checkbox changes after delivery to track progress
- Keep the change small by building on existing components

**Non-Goals:**
- Task-by-task delivery (single-shot prompt with all tasks is sufficient — AI tools handle ordered lists well)
- Formal task dependency graph (tasks are authored in execution order by convention)
- Modifying the OpenSpec CLI or schema
- Adding new delivery mechanisms beyond clipboard/editor-tab/API

## Decisions

### Decision 1: Reuse ArtifactInstruction.buildPrompt() pattern for task prompt assembly

**Choice:** Add a static method `buildApplyPrompt(String changeName, String changeDir)` on a new `ApplyPromptBuilder` utility (or directly in `OpenSpecApplyAction`) that reads design.md, specs, and tasks.md from the change directory and assembles them into a single implementation prompt.

**Rationale:** The prompt assembly logic is specific to the Apply action and doesn't fit the `ArtifactInstruction` model (which is per-artifact, not whole-change). A dedicated builder keeps concerns separated while following the same pattern.

**Alternatives considered:**
- Extending `ArtifactInstruction` with an apply mode: rejected because the Apply prompt shape (all context + all tasks) is fundamentally different from a single-artifact prompt.
- Using the CLI to assemble the prompt: rejected because the CLI has no `apply` command, and adding one is out of scope.

### Decision 2: Apply button appears in WorkflowActionPanel when all artifacts are done and tasks remain

**Choice:** When the artifact pipeline shows all-complete, the panel transitions from showing "Generate [artifact]" to showing "Apply Tasks" with the same tool selector and delivery flow. The pipeline chips remain visible to show artifact completion.

**Rationale:** This is the natural continuation of the workflow — Generate fills the pipeline, then Apply implements. Same panel, same UX patterns, different content.

**Alternatives considered:**
- Separate Apply panel: rejected as unnecessary — the workflow panel already manages the full lifecycle.
- Keep Apply as menu-only action: rejected because the user's workflow is already centered on the panel.

### Decision 3: Watch tasks.md for checkbox changes as progress signal

**Choice:** After delivering the apply prompt, use `ArtifactFileWatcher` (or a similar VFS listener) on `tasks.md` to detect when the AI tool marks tasks as complete (`- [ ]` → `- [x]`). On change detection, parse the file and update the task progress display.

**Rationale:** The existing file watcher pattern works well for Generate (watches for artifact creation). For Apply, the signal is modification of tasks.md rather than creation of a new file. Same mechanism, different trigger.

**Alternatives considered:**
- Manual "Check progress" button only: simpler but less responsive. Keep as fallback alongside file watching.

### Decision 4: Single-shot prompt, with a soft hint for large task lists

**Choice:** Always deliver all remaining tasks in one prompt. If 10+ tasks remain, show an inline hint: "Large task list — consider reviewing tasks.md first." No blocking dialog.

**Rationale:** AI tools handle ordered task lists well. Breaking into chunks adds complexity and friction. The hint addresses the user's concern about large lists without imposing workflow constraints.

**Alternatives considered:**
- Task-by-task delivery: rejected — requires resending all context each time, adds friction, and AI tools don't need it.
- Chunked delivery (groups of 5): rejected — arbitrary chunking can split related tasks and confuse the AI.

## Risks / Trade-offs

- [tasks.md format varies across schemas] → Mitigation: parse standard markdown checkboxes (`- [ ]` / `- [x]`) which is schema-agnostic.
- [File watcher may not detect external edits promptly] → Mitigation: keep "Check progress" button as manual fallback, same pattern as Generate.
- [Large prompts may exceed AI tool context limits] → Mitigation: the prompt includes only design + specs + tasks (not proposal), and these are typically <5KB total. The 10+ task hint encourages review.
- [Apply button placement may confuse with existing Generate flow] → Mitigation: Apply button only appears when all artifacts are done, making the transition clear.
