## Why

Two services write files directly to disk via `Files.writeString()`, bypassing IntelliJ's Virtual File System (VFS). This means the IDE doesn't know the files exist until a manual or timed refresh occurs, causing the tree view to appear stale and file lookups to fail silently. The `ArtifactFileWatcher` mitigates this with a 5-second polling fallback, but users still experience a visible delay where generated artifacts don't appear.

## What Changes

- Replace `Files.writeString()` / `Files.createDirectories()` in `ArtifactOrchestrationService.writeArtifactResult()` with VirtualFile API calls inside `WriteAction`
- Replace `Files.writeString()` / `Files.readString()` in `TrackingMetadataWriter` with VirtualFile API calls inside `WriteAction`
- Add explicit `VfsUtil.markDirtyAndRefresh()` after both write paths to ensure immediate VFS visibility

## Capabilities

### New Capabilities

_None — this is a bug fix to existing file-writing internals._

### Modified Capabilities

- `plugin-core`: Add requirement that all file write operations must use VirtualFile API within WriteAction to maintain VFS consistency

## Impact

- `ArtifactOrchestrationService.writeArtifactResult()` — changes from `java.nio.file` to VirtualFile API
- `TrackingMetadataWriter.writeYaml()` / `readYaml()` — changes from `java.nio.file` to VirtualFile API
- No public API changes — callers are unaffected
- Tree view and file watcher will see files immediately instead of after 5-second polling delay
