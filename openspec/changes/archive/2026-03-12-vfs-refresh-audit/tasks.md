## 1. ArtifactOrchestrationService VFS Migration

- [x] 1.1 Replace `Files.createDirectories()` + `Files.writeString()` in `writeArtifactResult()` with `VfsUtil.createDirectoryIfMissing()` + `VirtualFile.setBinaryContent()` inside `WriteAction`
- [x] 1.2 Add `VfsUtil.markDirtyAndRefresh()` after artifact write as a safety net for parent directory indexing

## 2. TrackingMetadataWriter VFS Migration

- [x] 2.1 Replace `Files.readString()` in `readYaml()` with `VirtualFile.contentsToByteArray()` via `LocalFileSystem.getInstance().refreshAndFindFileByPath()`
- [x] 2.2 Replace `Files.writeString()` in `writeYaml()` with `VirtualFile.setBinaryContent()` inside `WriteAction`
- [x] 2.3 ~~Update `writeForgejoRef()` and `writePlaneRef()` to pass `Project` parameter~~ — Not needed: `WriteAction.runAndWait()` is application-level, no `Project` required

## 3. Verification

- [x] 3.1 Build compiles with no errors
- [x] 3.2 All existing tests pass
