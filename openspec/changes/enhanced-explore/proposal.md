## Why

The current Explore Context feature is a fire-and-forget action that copies project context to the clipboard. Users have no visibility into what context is being assembled, no way to preview or edit it, and no live refresh when specs or changes evolve. Promoting Explore to a dedicated tool window tab makes context assembly a first-class, always-visible feature aligned with the Browse, Coverage, and Console tabs already in the OpenSpec tool window.

## What Changes

- Add an **Explore** tab to the OpenSpec tool window (alongside Browse, Coverage, Console)
- Display assembled context (config summary, active changes, spec domains, detected tools) in a readable, scrollable panel
- Provide a **Copy to Clipboard** button preserving existing `ExploreContextAction` behavior
- Provide an **Open in Editor** button that opens the assembled context as an IntelliJ scratch file
- Auto-refresh the panel when files under `openspec/` change, using a VFS listener
- Extract context assembly logic into a dedicated service for reuse and testability

## Capabilities

### New Capabilities
- `explore-panel`: Tool window tab displaying assembled project context with copy and editor actions, and auto-refresh on openspec file changes

### Modified Capabilities
- `workflow`: Workflow action panel gains awareness of the new Explore tab for context delivery options

## Impact

- **New files**: `ExplorePanel.java` (tool window panel), `ExploreContextService.java` (context assembly service), `ExploreContextServiceTest.java` (tests)
- **Modified files**: `OpenSpecToolWindowFactory.java` (register Explore tab), `plugin.xml` (service registration), `ExploreContextAction.java` (delegate to shared service)
- **Dependencies**: IntelliJ `VirtualFileManager` / `BulkFileListener` for VFS watching, `ScratchFileService` for scratch file creation
- **No breaking changes**: Existing clipboard action continues to work; Explore tab is additive
