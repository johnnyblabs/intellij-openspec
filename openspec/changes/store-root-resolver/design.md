# Design

## Context

The plugin finds its artifacts through `OpenSpecFileUtil.getOpenSpecRoot` (12 call sites in 6 files) and about 44 hand-built `openspec/...` paths in 29 more files. All of them assume `<project>/openspec`.

Upstream (CLI 1.5.0+) resolves the root in `dist/core/root-selection.js`. This file is byte-identical from 1.16.0 through 2.2.0, and its order has not changed since 1.7.0 added `defaultStore`. These facts were verified live on 1.5.0, 1.7.0, 1.15.0, 2.0.0, 2.1.0 and 2.1.1 with an isolated home and XDG directories:

- **Order.** `--store` → nearest qualifying `openspec/` (planning shape: source `nearest`; config-only with `store:`: source `declared`) → global `defaultStore` (1.7.0+: source `global_default`) → error `no_root_with_registered_stores` when stores exist → per-command `implicit` or `no_openspec_root`.
- **Qualifying directory.** An `openspec/` qualifies when it has a `specs/` or `changes/` directory or a `config.yaml`/`config.yml` file. A bare directory is skipped.
- **Pointer only without a planning shape.** A planning shape wins, and the `store:` key is ignored with a stderr warning.
- **Store checks.** The id is registered, `.openspec-store/store.yaml` has the same id, and the root is healthy (`openspec/` exists with a config file).
- **Files.**
  - Pointer: `<proj>/openspec/config.yaml` `store:` (string).
  - Default: `<config-dir>/openspec/config.json` `"defaultStore"`, where config-dir is `$XDG_CONFIG_HOME`, else `~/.config` on macOS and Linux, else `%APPDATA%` on Windows.
  - Registry: `<data-dir>/openspec/stores/registry.yaml` `stores.<id>.backend.local_path`.
- **Paths are realpath-canonicalized.** Artifacts live at `<root>/openspec/{specs,changes,changes/archive}`.
- **The CLI follows the store from the app directory.** `list`, `validate --all`, `status`, `show`, `instructions`, `new change` and `archive` all act on the store when run from the app directory. The plugin already runs the CLI in the project directory and uses only verb forms, so its CLI paths follow the store with no flag.

Everything the resolution needs is a file, so it can be reproduced without the CLI.

## Goals / Non-Goals

**Goals:**
- One resolver whose answer matches the CLI's on every captured layout.
- Every surface reads from the resolved root; no behavior change for projects without a store.
- No disk, refresh or CLI work on the paths that read the root (EDT, action `update`, inspections, VFS listeners).

**Non-Goals:**
- Showing a local tree and a store tree together. Upstream treats them as mutually exclusive, so this would be off-model.
- A plugin-side choice of store, or `--store` from the UI.
- Attaching the store to the project model (content root or library).
- Suppressing the non-project write prompt, and store-naming wording. These belong to the second change.
- Referenced stores (`context.members`).
- WSL-hosted stores where the IDE and the CLI see different registries.

## Decisions

### D1. Resolve from disk; the CLI is not consulted

`OpenSpecRootResolver` reimplements the order from the files listed above.

**Why:**
- The rules are small, stable since 1.7 and identical through 2.2.0.
- Running `context --json` costs a Node start on every re-resolve, and the plugin must work without a CLI.
- Resolution parity with the CLI is proven by contract tests (D7), not by asking the CLI at runtime.

**Alternative rejected:** CLI first with a disk fallback. It is slower, two code paths decide the same thing, and with no CLI the plugin would still need the disk path.

### D2. Release gates use the followed release

The sources follow the release the plugin follows (`ValidationRelease.followed`: the detected CLI, else the target release).
- The `store:` pointer applies from 1.5.0.
- `defaultStore` applies from 1.7.0.
- Below 1.5.0 only `nearest` resolves.

The gates go into one small table in the resolver, since these are behaviors, not flags.

### D3. Problems are reported, not swallowed

When a pointer or default store fails a check, the snapshot carries `NONE` plus a problem `{code, message, fix}` using the CLI's codes. The tool window's empty state shows the message and fix instead of "no specs". `no_root_with_registered_stores` is not reported: unlike the CLI, the plugin offers init, and a registered store with no pointer is normal.

The CLI's warning for a pointer next to a local planning tree is also kept on the snapshot. In this change it is only logged; the second change shows it.

### D4. A cached project service, resolved on a pooled thread

`@Service(PROJECT) final class OpenSpecRootResolver implements Disposable`:

- **`snapshot()`.** Volatile, O(1). It never reads the disk, refreshes or runs the CLI. If the cached root `VirtualFile` is no longer valid, it schedules a re-resolve and returns the cached value.
- **`resolveAsync()`.** A pooled-thread `Alarm`, debounced 300 ms, owned by the service. It:
  1. reads the files with plain I/O, not PSI;
  2. refreshes and finds the root outside any read action, with `markDirtyAndRefresh` so the children load;
  3. swaps the watch roots;
  4. publishes `OpenSpecRootListener.rootChanged(old, now)` on a project topic.
- **Snapshot.** `kind` (`PENDING`, `LOCAL`, `STORE`, `NONE`), source, store id, the effective root path and `VirtualFile`, an optional problem and an optional warning.
- **Warm-up.** It starts from the existing startup `ProjectActivity`. Until then the kind is `PENDING`.

`OpenSpecRoot` is a value object over the snapshot: `root()`, `configFile()`, `specsDir()`, `changesDir()`, `archiveDir()`, `changeDir(name)`, `mainSpec(capability)`, `contains(VirtualFile)`, and `localAnchor()` for init.

### D5. `OpenSpecFileUtil` becomes a facade; the refresh fallback goes

`getOpenSpecRoot`, `getConfigFile`, `getSpecsDir`, `getChangesDir`, `getArchiveDir`, `isUnderOpenSpec` and `isOpenSpecProject` delegate to the snapshot. The synchronous `refreshAndFindFileByPath` fallbacks (`OpenSpecFileUtil.java:36-37`, `:49-50`) are removed. They ran inside VFS `after()` callbacks, in `OpenSpecBaseAction.update` and in the status-bar widget factory whenever `openspec/` was missing.

`isUnderOpenSpec` uses `VfsUtilCore.isAncestor` on canonical files instead of a string prefix.

**A fast in-memory check comes first.** Before reading the snapshot, the facade checks the in-memory file system, with no refresh: `<project>/openspec` with a planning shape, then any module content root's `openspec/` (as `getOpenSpecRoot` does today, for multi-module projects and for test fixtures on `temp://`). A content root that is the project directory itself must have a planning shape too, so a pointer-only config there still goes to the resolver. A hit is what the CLI resolves from the project directory, so it needs no cache. The rest (ancestors, config-only directories, pointers, the default store) comes from the snapshot. The CLI knows nothing of content roots, so a content-root hit beats an ancestor root. That is today's behavior, kept on purpose. Only the refresh fallback is removed.

**The upward walk is new behavior.** The CLI walks from the project directory up through its ancestors, so a project opened inside a larger OpenSpec repo resolves to that repo's root. The plugin follows the CLI. Today such a project shows as not set up. The global default store is likewise new: a project with no qualifying `openspec/` on a machine with `defaultStore` set now shows that store, as the CLI does.

**The config inspection skips its schema nudge on a pointer-only config.** `ConfigValidationInspection` suggests adding `schema:` when it is missing. On a config whose only job is `store:`, the store's config governs the schema, so the nudge is wrong there.

Then the hand-built sites move onto `OpenSpecRoot`, surface by surface. `ScaffoldingService` keeps `localAnchor()`. Display strings stay root-relative here; naming the store is the second change.

### D6. Watching and invalidation

- **What is watched.** The service holds `WatchRequest`s for `<root>/openspec` (recursive) and the registry and global-config directories (non-recursive). It swaps them with `replaceWatchedRoot` and removes them on dispose.
- **What re-resolves.** Its own `BulkFileListener` re-resolves on these paths:
  - `<project>/openspec`, created or deleted;
  - `<project>/openspec/config.yaml` or `config.yml`;
  - the registry file;
  - the global config file;
  - deletion of the effective root.
- **Explicit triggers.** `OpenSpecRefreshAction` and the plugin's own store writes in `CoordinationService` call `invalidate()`.
- **Existing listeners.** The tool-window and Getting Started listeners already filter through `isUnderOpenSpec`, so they see store events once it delegates. `ArtifactFileWatcher` gets its change directory from `OpenSpecRoot`.

### D7. Contract tests against captured CLI output

`scripts/capture-store-root.py` installs each release into scratch with npm, never a pinned `npx`. It builds these layouts under an isolated `HOME`, `XDG_DATA_HOME` and `XDG_CONFIG_HOME`:
- nearest;
- nearest in an ancestor directory;
- nearest with a pointer;
- declared, including from a subdirectory;
- global default;
- unknown store;
- malformed pointer;
- bare directory;
- stores registered with no pointer.

It also covers an identity mismatch and an unhealthy store, appended to the registry by hand because the CLI won't register them. For each scenario it records `context --json`, stderr and the exit code.

The output is `world.json` (every directory and file, the XDG directories, and per phase the registry and global config as the CLI wrote them) and `scenarios.json`. Paths are sanitized to `<W>`. Fixtures go under `src/test/resources/fixtures/cli/<ver>/store-root/` for 1.5.0, 1.7.0, 1.15.0, 2.0.0, 2.1.0 and 2.1.1. On 1.5.0 the default store is written with `--allow-unknown`, so that release's capture really holds one and shows it ignored.

`StoreRootContractTest` recreates each layout and resolves it with the fixture's release as the followed release. It asserts the CLI's path, source, store id, problem code, message and fix, and the pointer-ignored warning, word for word. The one exception is the YAML or schema detail inside `invalid_store_metadata`, which comes from upstream's parser libraries; there it compares up to that detail. On 1.5.0 the global-default layouts must resolve to no root. Each capture records `openspec --version` and the npm integrity, and the test checks the version matches the folder.

The layouts also cover the cases a store pointer can go wrong in: no store registered, an invalid store id, a `config.yml` pointer, an empty `store:` key, unparseable YAML, a stale or empty default store, store metadata that is unparseable, lacks `version: 1` or has an unknown key, and a registered path that is a file. The Gradle test task points the CLI's machine-wide folders at empty build folders, so a developer's own stores never reach a test.

### D8. Hygiene test

`OpenSpecRootLiteralHygieneTest` scans `src/main/java` for `"/openspec/"`, `"openspec/specs"`, `"openspec/changes"`, `findChild("openspec")` and `Path.of(.., "openspec", ..)` outside the resolver package. Its allow-list covers the unrelated uses: global data dir names, CLI install hints, tool detection, resources and init's local anchor.

## Risks / Trade-offs

- **Drift if upstream changes the rules.** Mitigation: the capture script runs at every CLI bump, and the contract test fails on any difference.
- **A large mechanical diff (about 56 sites).** Mitigation: the facade lands first with no behavior change. The rest moves in surface batches, each covered by its tests, and the hygiene test catches stragglers.
- **Unwatchable roots** (network drive, inotify limit) show changes only after Refresh. That is acceptable; Refresh re-resolves.
- **Inspections on non-project files in other IDEs** depend on there being no `ProblemHighlightFilter` that suppresses them. Mitigation: check in a non-IDEA IDE before release.
- **Outside changes to a store** appear as a project file's do: when the user returns to the IDE, through the platform's window-activation sync. The store-root uiSmoke journey proves that end to end. It does not prove the watch itself, since that sync also re-reads folders nobody watches; the watch only flags changes sooner. The resolver also loads a newly resolved store tree into the VFS once, because the VFS reports changes only under loaded folders and a store isn't indexed.
- **WSL:** the IDE's registry differs from a WSL CLI's. Out of scope; documented as unsupported.
- **The plugin-core 1.9 scenario** says the plugin gates CLI invocation on the on-disk `openspec/` root. That remains true with the resolved root read as that root, so no delta is needed.

## Migration Plan

None. Projects with a local planning tree resolve to `<project>/openspec` exactly as before. Two kinds of project that showed as not set up now resolve a root, as the CLI already does for them: one nested inside a larger OpenSpec repo, and one with no `openspec/` on a machine with a default store. Rollback is reverting the change.

## Open Questions

None blocking. Showing the pointer-ignored warning and naming the store in the UI are decided in the second change.
