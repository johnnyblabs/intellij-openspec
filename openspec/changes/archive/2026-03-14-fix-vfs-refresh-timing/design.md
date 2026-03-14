## Context

`VfsUtil.markDirtyAndRefresh(async, recursive, reloadChildren, files)` — the first parameter controls sync vs async. We pass `false` which means **synchronous** — but the current code immediately checks `baseDir.findChild("openspec")` which may not find it if the filesystem operations haven't completed within the VFS index.

`VirtualFileManager.asyncRefresh()` explicitly defers refresh to later. The callback fires on the EDT after refresh completes, but by then `GettingStartedPanel` has already checked `detectState()`.

## Goals / Non-Goals

**Goals:**
- Config.yaml visible to validation immediately after init returns
- New changes visible to GettingStartedPanel immediately after propose completes
- No UI race conditions on file visibility

**Non-Goals:**
- Refactoring the entire VFS strategy
- Making background operations synchronous (only the refresh step)

## Decisions

### Use synchronous refresh in ScaffoldingService.initOpenSpec()

The `VfsUtil.markDirtyAndRefresh(false, true, true, baseDir)` call is already sync (`false` first param). The issue is that after CLI init, the `openspec/` directory was created outside VFS (by the CLI process). We need to call `LocalFileSystem.getInstance().refreshAndFindFileByPath()` for the specific path, which does a synchronous disk-to-VFS sync.

### Replace asyncRefresh with syncRefresh in refreshToolWindow()

`VirtualFileManager.getInstance().syncRefresh()` runs the VFS refresh synchronously on the current thread. Then we can `invokeLater` to update the UI, knowing the VFS state is current. This ensures `detectState()` sees the newly created change.

### Keep GRADLE_OPTS change minimal

Just add `GRADLE_OPTS: -Dorg.gradle.daemon=false` to the workflow env block. No Gradle config files needed.

## Risks / Trade-offs

- **[Low] Sync refresh may block EDT briefly** → VFS sync refresh on a small directory tree (`openspec/`) is fast (<100ms). Acceptable for a one-time init or propose operation.
