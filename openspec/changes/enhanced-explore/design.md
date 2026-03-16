## Context

The OpenSpec tool window currently has three tabs: Browse (tree view), Coverage (spec coverage), and Console (CLI output). The Explore Context feature exists only as a menu action (`ExploreContextAction`) that assembles project context and copies it to the clipboard. There is no persistent UI for viewing, inspecting, or refreshing this context. Users must invoke the action each time and cannot preview what they are copying.

The codebase already uses `BulkFileListener` via `ArtifactFileWatcher` for VFS watching, `ScratchFileService` is available in the IntelliJ Platform SDK, and all tool window tabs follow the same `JPanel` + `ContentFactory` registration pattern in `OpenSpecToolWindowFactory`.

## Goals / Non-Goals

**Goals:**
- Provide a dedicated Explore tab in the OpenSpec tool window for always-visible project context
- Extract context assembly into a reusable `ExploreContextService` shared by both the panel and the existing action
- Support clipboard copy and scratch-file-in-editor delivery of assembled context
- Auto-refresh panel content when files under `openspec/` are created, modified, or deleted
- Maintain full backward compatibility with the existing `ExploreContextAction`

**Non-Goals:**
- Custom formatting or theming of the context display beyond a read-only text area
- Filtering or selective assembly of context sections (all sections are always shown)
- Integration with Direct API for Explore context (that belongs to a future enhancement)
- Editing context directly in the Explore panel

## Decisions

### 1. Extract `ExploreContextService` as a project-level service

**Decision:** Create `ExploreContextService` annotated with `@Service(Service.Level.PROJECT)` to own all context assembly logic. Both `ExplorePanel` and `ExploreContextAction` delegate to it.

**Rationale:** The existing `ExploreContextAction` contains context assembly logic inline. Extracting it makes the logic testable in isolation, avoids duplication, and follows the project's established service pattern (ConfigService, ChangeService, SpecCoverageService).

**Alternative considered:** Keeping assembly in the action and having the panel call the action programmatically. Rejected because actions are UI-bound and harder to test.

### 2. `ExplorePanel` as a `JPanel` registered in `OpenSpecToolWindowFactory`

**Decision:** Add `ExplorePanel` as the fourth tab in `createNormalContent()`, following the same pattern as `SpecCoveragePanel` (extends `JPanel`, toolbar + scrollable content area).

**Rationale:** Consistent with existing tab structure. The panel will contain a toolbar with Copy and Open in Editor buttons, and a `JBTextArea` (read-only) in a `JBScrollPane` for the assembled context.

**Alternative considered:** Using an `EditorTextField` for syntax-highlighted Markdown. Rejected for now as over-engineering; a plain text area matches the exploratory nature of the feature and avoids editor lifecycle complexity.

### 3. VFS listener scoped to `openspec/` directory

**Decision:** Subscribe to `VirtualFileManager.VFS_CHANGES` via the project message bus, filtering events to paths containing `/openspec/`. Use a 500ms debounce timer to coalesce rapid file changes before triggering a refresh.

**Rationale:** The `ArtifactFileWatcher` pattern already proves this approach works in the codebase. A debounce prevents excessive refreshes during bulk operations (e.g., archiving multiple changes). The listener lifecycle is tied to the panel's `Disposable` to prevent leaks.

**Alternative considered:** Polling with a periodic timer (like `ArtifactFileWatcher`'s fallback). Rejected because the Explore panel is always visible and event-driven refresh is more responsive and resource-efficient.

### 4. Scratch file for "Open in Editor"

**Decision:** Use `ScratchRootType.getInstance().createScratchFile()` to create a Markdown scratch file and open it via `FileEditorManager`.

**Rationale:** Scratch files are the IntelliJ-idiomatic way to open ephemeral content in the editor. They persist across IDE restarts (useful for reference) but do not pollute the project tree. Using `.md` extension gives free Markdown highlighting.

**Alternative considered:** Writing to a temp file and opening it. Rejected because temp files lack IDE integration and get cleaned up unpredictably.

## Risks / Trade-offs

- **[Performance] Large projects with many specs/changes could produce large context strings** --> Mitigation: Context assembly already truncates proposal summaries to 500 chars. The service will assemble on a pooled thread and update the UI on EDT, matching the existing action's threading model.

- **[VFS event volume] Rapid file changes could cause excessive refreshes** --> Mitigation: 500ms debounce timer coalesces events. The timer resets on each new event within the window.

- **[Scratch file accumulation] Repeated "Open in Editor" clicks create multiple scratch files** --> Mitigation: Reuse a single scratch file per project by caching the `VirtualFile` reference in the service and overwriting its content on subsequent opens.

## Open Questions

- Should the Explore tab be the default selected tab when the tool window opens, or should Browse remain the default? (Recommendation: Keep Browse as default for v0.3.0, revisit based on usage.)
