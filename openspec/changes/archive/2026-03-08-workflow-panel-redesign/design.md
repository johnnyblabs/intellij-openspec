## Context

The `WorkflowActionPanel` is a `JPanel` using a `CardLayout` with three cards: "normal" (change selector + pipeline + generate button), "setup" (first-run delivery choice), and "guidance" (post-clipboard instructions). Pipeline chips are small text labels with unicode status icons (✓ ● ○) in 11pt font. The Generate button says "Generate {artifactId}" with no indication of what will happen. After clipboard delivery, the guidance card shows static instructions until the user clicks "Check for updates." There is no file watching, no click-to-open for completed artifacts, and no regeneration capability.

The panel is embedded in the tool window between the change tree and the status bar.

## Goals / Non-Goals

**Goals:**
- Make the pipeline a first-class interactive element — clickable, informative, and visually clear
- Show the delivery method on the Generate button so users know what happens before they click
- Auto-detect artifact file changes after clipboard/editor delivery to close the feedback loop
- Allow opening and regenerating completed artifacts from the pipeline
- Make the overall flow feel like guided steps, not a status dashboard

**Non-Goals:**
- Changing the underlying artifact orchestration or DAG logic
- Redesigning the change tree or tool window frame
- Adding new delivery methods (that's in the settings-panel-redesign change)
- Multi-change parallel generation

## Decisions

### 1. Interactive pipeline chips with tooltips and click actions

**Decision**: Replace the current text-only chips with `JPanel`-based chips that support:
- **Hover tooltip** showing a brief description of the artifact's role (e.g., "proposal: establishes why this change is needed")
- **Left-click on completed chips** opens the artifact file in the editor via `FileEditorManager`
- **Right-click on completed chips** shows a popup menu with "Open" and "Regenerate" actions
- **Visual state**: larger font (12pt), bolder icons, highlighted border on the current/ready artifact

Artifact descriptions are static metadata:
| Artifact | Description |
|----------|-------------|
| proposal | Why this change is needed |
| design | How to implement it |
| specs | What to build (requirements) |
| tasks | Implementation checklist |

**Alternative considered**: A vertical stepper (like Material Design). Rejected because the panel is horizontally oriented between the tree and buttons, and the pipeline is compact (typically 4 items). Horizontal chips fit the layout better.

### 2. Delivery-aware Generate button

**Decision**: The Generate button text includes the delivery method: "Generate design → clipboard", "Generate specs → API", "Generate proposal → editor tab". This replaces the current "Generate {artifactId}" label.

The delivery label comes from `DeliveryMethodResolver.resolve()`. The dropdown chevron remains for switching methods.

**Rationale**: Users should know what happens before clicking. "Generate design" is ambiguous. "Generate design → clipboard" is actionable.

### 3. Artifact file watcher for auto-refresh

**Decision**: After clipboard or editor-tab delivery, register a `VirtualFileListener` on the expected output path. When the file appears or changes, automatically:
1. Invalidate the DAG cache
2. Refresh the pipeline chips
3. Transition from the guidance card back to normal view with the next artifact ready

Use IntelliJ's `VirtualFileManager.addVirtualFileListener()` with a filter on the change directory. Unregister on panel disposal or when switching to a different change.

**Timeout**: If no file change is detected after 10 minutes, dismiss the watcher and show a hint: "Artifact not detected yet. Click 'Check for updates' to refresh manually."

**Alternative considered**: Polling with a timer. Rejected because IntelliJ's VFS already provides event-driven file change notifications, which is more efficient and responsive.

### 4. Guidance card becomes an inline waiting state

**Decision**: Instead of a separate card that replaces the entire panel, show the guidance as an inline status beneath the pipeline:

```
✓ proposal → [● design (waiting...)] → ○ specs → ○ tasks

Instructions copied to clipboard. Paste into Claude Code.
Watching for design.md...                    [Copy again]
```

The pipeline chips remain visible (so users still see where they are), and the currently-waiting artifact chip shows an animated or pulsing indicator. When the file is detected, it transitions smoothly to the next ready state.

**Alternative considered**: Keeping the full-card guidance that replaces the panel. Rejected because it hides the pipeline context and feels like a dead end.

### 5. Regeneration via ArtifactOrchestrationService

**Decision**: Add a `regenerateArtifact(changeName, artifactId)` method to `ArtifactOrchestrationService` that:
1. Invalidates the DAG cache
2. Treats the artifact as if it's "ready" regardless of current status
3. Returns the instruction for that artifact

The panel calls this when the user selects "Regenerate" from the chip context menu, then runs the normal generation flow. The existing artifact file is overwritten.

**Guard**: Show a confirmation dialog before regenerating if the artifact has downstream dependents that are already complete (e.g., regenerating "proposal" when "design" is done).

### 6. Chip descriptions as static map

**Decision**: Store artifact descriptions as a static map in the panel rather than adding metadata to the CLI or DAG model:

```java
Map.of("proposal", "Why this change is needed",
       "design", "How to implement it",
       "specs", "What to build",
       "tasks", "Implementation checklist")
```

Unknown artifact IDs fall back to the ID itself. This avoids CLI changes and covers the standard spec-driven schema.

## Risks / Trade-offs

**[File watcher reliability]** → VFS listeners may miss external file changes (e.g., file written by Claude Code CLI outside IntelliJ). → Mitigation: Call `VirtualFileManager.refreshAndFindFileByUrl()` periodically (every 5 seconds) as a fallback alongside the event listener.

**[Regeneration can break downstream]** → Regenerating "proposal" after "design" is complete could make the design inconsistent. → Mitigation: Confirmation dialog warning about downstream artifacts. Don't cascade-delete downstream files — let the user decide.

**[Inline guidance takes vertical space]** → The pipeline + guidance text is more compact than the current full-card approach, but the panel height may still grow with 2-3 lines of guidance text. → Mitigation: Keep guidance to a single line when possible, collapse to icon + text.
