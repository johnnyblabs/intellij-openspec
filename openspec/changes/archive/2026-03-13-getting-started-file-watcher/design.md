## Context

`GettingStartedPanel` detects project state (`NOT_INITIALIZED`, `NO_AI_CONFIGURED`, `NO_CHANGES`, `READY`) in `detectState()` and renders UI accordingly. The transition to the normal tree view only happens when the user clicks "Propose a Change" inside the panel (line 167). There is no VFS listener, so external changes (CLI, AI tools) are invisible.

`OpenSpecToolWindowPanel` already has a working pattern: it subscribes to `VirtualFileManager.VFS_CHANGES` via the project message bus and debounces refreshes with an `Alarm`. The same approach applies here.

## Goals / Non-Goals

**Goals:**
- GettingStartedPanel reacts to filesystem changes under `openspec/` and re-evaluates its state
- When state advances to `READY`, automatically transition to the normal tree view
- When state advances between other states (e.g., `NOT_INITIALIZED` → `NO_AI_CONFIGURED`), rebuild the panel in place
- Clean up the listener when the panel is removed

**Non-Goals:**
- Changing the existing in-panel button behavior (it already works)
- Adding refresh to other panels (they already have it)

## Decisions

### Decision 1: Use BulkFileListener on project message bus

Same pattern as `OpenSpecToolWindowPanel.registerFileListener()`. Subscribe to `VirtualFileManager.VFS_CHANGES`, filter for events under the `openspec/` directory, and debounce with an `Alarm`.

Alternative considered: polling on a timer. Rejected because the VFS listener pattern is already established in the codebase and is more responsive.

### Decision 2: Implement Disposable for cleanup

The `GettingStartedPanel` needs to disconnect its message bus subscription when it's removed from the tool window (i.e., when transitioning to the tree view). Use `Disposer.register()` with the tool window's disposable, or use `project.getMessageBus().connect(disposable)` with a `Disposer`-managed disposable.

### Decision 3: Re-evaluate state on each VFS event, transition if needed

On each debounced VFS event:
1. Call `detectState()`
2. If state is `READY` and `toolWindow` is available, replace content with `createNormalContent()`
3. If state changed but not to `READY`, call `rebuild()` to update the panel in place

## Risks / Trade-offs

- **[Low risk] Race condition with button handler** → Both the button click and the VFS listener could try to transition simultaneously. The `removeAllContents` + `createNormalContent` sequence is idempotent, so worst case is a harmless double-rebuild.
- **[Low risk] VFS refresh timing** → External CLI changes may not be immediately visible in the VFS. The `asyncRefresh` that `OpenSpecBaseAction` already triggers covers the in-IDE case; CLI changes are picked up by IntelliJ's periodic VFS sync.
