## Why

Two bugs stem from async VFS refresh timing:
1. After CLI-delegated init, validation runs before VFS indexes the new `config.yaml`, producing a false `[ERROR] config-missing` error
2. After creating a change via the Propose dialog, `refreshToolWindow()` uses `asyncRefresh()` so `GettingStartedPanel.detectState()` doesn't see the new change — the UI stays on "Create your first change" instead of transitioning to the tree view

Both are caused by checking for files before IntelliJ's VFS has finished indexing them.

## What Changes

- Fix `ScaffoldingService.initOpenSpec()` — use synchronous VFS refresh after CLI init so `config.yaml` is visible before returning
- Fix `OpenSpecBaseAction.refreshToolWindow()` — use `VirtualFileManager.syncRefresh()` instead of `asyncRefresh()` so the tree sees new changes immediately
- Add `GRADLE_OPTS: -Dorg.gradle.daemon=false` to CI workflow (prevents unnecessary daemon startup in ephemeral containers)

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

- `plugin-core`: VFS refresh operations after file writes must be synchronous

## Impact

- `ScaffoldingService.java` — synchronous VFS refresh after CLI init
- `OpenSpecBaseAction.java` — `refreshToolWindow()` uses sync refresh
- `.forgejo/workflows/build.yaml` — GRADLE_OPTS env var
