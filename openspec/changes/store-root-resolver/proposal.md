# Proposal

## Why

Every plugin surface assumes the OpenSpec root is `<project>/openspec`. Since CLI 1.5 a project can instead point at a registered store with `store:` in `openspec/config.yaml`, and since 1.7 a machine-wide `defaultStore` can supply the root. In both cases the CLI works on the store's tree, while the plugin shows an empty project and validates, archives and syncs nothing. Tracked in the linked tracker entry. This is the first of two changes; the second covers editing store files and naming the store in the UI.

## What Changes

- **One resolver for the OpenSpec root.** A project service resolves the root the way the CLI does: the nearest qualifying `openspec/` in the project directory or its ancestors first, then a declared `store:` pointer, then the global `defaultStore` (CLI 1.7+), then no root. It reads only files on disk (the project config, the global config and the store registry), so it works with or without a CLI, and it runs off the UI thread and caches its answer.
- **A store's own checks apply.** A store root is used only when its registry entry exists, its `.openspec-store/store.yaml` id matches, and its `openspec/` tree is present. Otherwise the plugin shows the CLI's matching problem (unknown store, malformed pointer, identity mismatch, unhealthy root) instead of an empty tree.
- **Every surface follows the resolved root.** The spec tree, the changes list, validation (built-in and CLI), archive, Sync Specs, the workflow panel, Explore's context, new-change creation, icons and inspections read from the resolved root instead of building `<project>/openspec` paths. `openspec init` still writes the local `openspec/`.
- **Changes in a store are watched.** The resolved root is watched for file changes even when it lies outside the project, and the resolver re-resolves when the pointer, the global config or the registry changes. Refresh also re-resolves.
- **A hygiene test keeps it that way.** A test fails on a hard-coded `openspec` root path outside the resolver and an allow-list.
- **Fixes a refresh inside file-event handling.** Today the root lookup can force a synchronous file-system refresh from inside file-change callbacks and action updates when no `openspec/` exists. The resolver's lookup never refreshes.
- **Out of scope (second change):** suppressing the "file does not belong to the project" prompt when editing store files, store-naming wording in the tree, panels and prompts, and a UI smoke journey.

## Capabilities

### New Capabilities

- `openspec-root-resolution`: how the plugin finds the OpenSpec root (local, declared store, global default store), the store checks, which CLI releases each source applies to, and that every surface reads from the resolved root.

### Modified Capabilities

None. Existing capabilities keep their requirements; they now operate on the resolved root, which equals `<project>/openspec` for every project with a local planning tree. Two kinds of project that show as not set up today will resolve a root, as the CLI already does: one opened inside a larger OpenSpec repo, and one with no `openspec/` on a machine with a default store.

## Impact

- **Code:** a new root resolver service and root value object; `OpenSpecFileUtil` becomes a facade over it; about 56 path sites move onto it (tool window, workflow panel, `ChangeService`, `SpecSyncService`, `BuiltInValidator`, `VerificationService`, `ArchiveReadinessService`, `ArtifactOrchestrationService`, `ExploreContextService`, `ConfigService`, new-change and validate actions, icon provider, inspections, `ArtifactFileWatcher`). `ScaffoldingService` keeps writing the local `openspec/`.
- **Tests:** contract tests against captured `context --json` output and on-disk store layouts from CLI 1.5.0, 1.7.0, 1.15.0, 2.0.0, 2.1.0 and 2.1.1; resolver unit tests on real temp directories; `OpenSpecRootLiteralHygieneTest`.
- **Fixtures:** a capture script that installs each release into scratch (never a pinned `npx`) with an isolated home and XDG directories.
- **Docs:** CHANGELOG, feature reference.
- **Platform:** uses `LocalFileSystem` watch roots and VFS refresh APIs that exist on 2024.2; no project-model changes (the store is not attached as a content root or library). IntelliJ 2024.2+ support is unaffected; `verifyPlugin` runs on the new platform references.
