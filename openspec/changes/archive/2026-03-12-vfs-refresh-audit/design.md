## Context

Two file-writing code paths bypass IntelliJ's VFS:

1. **`ArtifactOrchestrationService.writeArtifactResult()`** — uses `Files.createDirectories()` + `Files.writeString()` to write generated artifact content. The VFS never learns about these files until the 5-second `ArtifactFileWatcher` polling fallback fires.

2. **`TrackingMetadataWriter.writeYaml()`** — uses `Files.writeString()` to update `.openspec.yaml` tracking metadata. `readYaml()` also uses `Files.readString()`, which can return stale content if the VFS has pending writes.

All other file operations in the codebase (ScaffoldingService, ChangeService, WorkflowActionPanel) already use `WriteAction` + VirtualFile API correctly.

## Goals / Non-Goals

**Goals:**
- All file writes go through VirtualFile API within `WriteAction` so the VFS is immediately aware
- Generated artifacts appear in the tree view instantly instead of after a polling delay
- Tracking metadata updates are visible to VFS listeners (ConfigService, tree refresh)

**Non-Goals:**
- Changing the ArtifactFileWatcher polling mechanism (it serves as a safety net for externally-modified files)
- Refactoring ScaffoldingService or ChangeService (they already use VFS correctly)
- Adding new VFS listeners or refresh triggers

## Decisions

### Use `WriteAction.run()` + VirtualFile API for both write paths

Replace `Files.writeString()` with `VirtualFile.setBinaryContent()` inside `WriteAction.run()`. Use `VfsUtil.createDirectoryIfMissing()` for parent directory creation.

**Why not `WriteAction.compute()`?** Neither caller needs a return value from the write operation.

**Why not just add `VfsUtil.markDirtyAndRefresh()` after the `Files.writeString()`?** That would fix visibility but leaves a race window — another thread could try to access the file between the write and the refresh. Using VirtualFile API within WriteAction is atomic from the VFS perspective.

### Keep TrackingMetadataWriter reading via VirtualFile too

Switch `readYaml()` from `Files.readString()` to `VirtualFile.contentsToByteArray()`. This ensures we read the VFS-cached version, which is always consistent with pending WriteAction writes.

### Add `VfsUtil.markDirtyAndRefresh()` as a post-write safety net in `writeArtifactResult()`

Even though `WriteAction` + VirtualFile API should be sufficient, an explicit sync refresh after artifact writes ensures parent directories are fully indexed. This is belt-and-suspenders for a critical user-visible path.

## Risks / Trade-offs

- **WriteAction on EDT risk** → `WriteAction.run()` must execute on the EDT or a write-safe thread. `ArtifactOrchestrationService.writeArtifactResult()` is called from background threads. Mitigation: wrap in `ApplicationManager.getApplication().invokeLater()` + `WriteAction.run()`, or use `WriteCommandAction` if PSI is involved (it isn't here).
- **TrackingMetadataWriter used outside IDE context** → If this class is ever used from tests or CLI tooling without an IntelliJ Application, VirtualFile API won't work. Mitigation: The class is only used from plugin code within `ChangeService`, so this is safe. Add a comment noting the IDE dependency.
