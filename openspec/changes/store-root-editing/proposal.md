# Proposal

## Why

Since the plugin resolves the OpenSpec root as the CLI does, a project can show and edit specs that live in a store outside the project, or in a parent folder. Nothing tells the user so. Editing a store file also raises the platform's "This file does not belong to the project" prompt, and the edit isn't tracked by the project's version control, so the user has to know to commit it from the store's own repository. When the project's own planning tree overrides a `store:` line, the plugin ignores the store silently, while the CLI warns. Tracked in the linked tracker entry; this is the second of two changes, after `store-root-resolver`.

## What Changes

- **The Browse tree says where the specs live.** When the root isn't the project's own `openspec/`, the tree's root reads, for example, "OpenSpec · store demo (declared)", "OpenSpec · store demo (machine default)" or "OpenSpec · parent folder". Its tooltip gives the full path, and for a root outside the project says to commit changes from that folder's own repository. A project with its own `openspec/` keeps the plain "OpenSpec" root.
- **An ignored pointer is shown.** When `openspec/config.yaml` names a store but the project has its own planning tree, the tree shows a hint saying the store is ignored, and a notification says so once when it starts happening.
- **Store files edit like project files.** The platform's "does not belong to the project" prompt no longer appears for files under the resolved root. An editor banner on those files names the store and says to commit from the store's own repository. The banner can be dismissed for the project.
- **Store problems name the store.** The Getting Started card and the tree hint read "The OpenSpec store 'demo' can't be used" when the store id is known.
- **A hint never initializes over a store.** Double-clicking a hint directly under the tree's root runs Initialize. That is right for the "No openspec/ directory" hint, but `store-root-resolver` added a root-level hint for an unusable store, and this change adds one for an ignored pointer. Initializing there would create a local planning tree that silently overrides the store. Only the not-set-up hint initializes.
- **One wording helper** builds every label, tooltip, hint and banner, so they can't drift.
- **The store UI smoke journey checks the label and the banner.**
- **Out of scope:** committing the store from the IDE, showing the store's version-control state, choosing a store in the plugin, and showing a project's own tree beside a store's (upstream treats them as exclusive).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `openspec-root-resolution`: new requirements for showing where the specs live, surfacing an ignored pointer, editing store files without the not-in-project prompt, keeping tree hints from initializing over a store, and naming the store in its problems.
- `ui-smoke-journeys`: the store-root journey also checks the tree's provenance label and the editor banner.

## Impact

- **Code:** a wording helper in the `root` package; `SpecTreeModel` (root node as provenance data with a stable path id, ignored-pointer hint, a hint kind); `SpecTreeCellRenderer` (root icon and tooltip); `OpenSpecToolWindowPanel` (root detection by type, double-click initializes only from the not-set-up hint); `GettingStartedPanel` (card title); a `NonProjectFileWritingAccessExtension` and an `EditorNotificationProvider` registered in `plugin.xml`; a one-time notification on the resolver's root change.
- **Tests:** `TreeRefreshStateTest` (root no longer a string); unit tests for the wording helper; platform tests for the write-access extension (with the platform's non-project checks enabled in tests), the banner provider and the tree root; the uiSmoke journey.
- **Docs:** CHANGELOG, feature reference.
- **Platform:** `nonProjectFileWritingAccessExtension` and `editorNotificationProvider` exist on 2024.2; both are checked by `verifyPlugin`. IntelliJ 2024.2+ support is unaffected.
