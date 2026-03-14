## 1. Fix VFS refresh in ScaffoldingService

- [x] 1.1 Replace `VfsUtil.markDirtyAndRefresh` + `findChild` with `LocalFileSystem.refreshAndFindFileByPath()` for the openspec directory after CLI init

## 2. Fix VFS refresh in refreshToolWindow

- [x] 2.1 Replace `VirtualFileManager.asyncRefresh()` with `VirtualFileManager.syncRefresh()` in `OpenSpecBaseAction.refreshToolWindow()`

## 3. CI improvement

- [x] 3.1 Add `GRADLE_OPTS: -Dorg.gradle.daemon=false` to workflow env

## 4. Verify

- [x] 4.1 Build plugin and confirm zero compilation errors
