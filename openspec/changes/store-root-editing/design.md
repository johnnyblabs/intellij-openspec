# Design

## Context

`store-root-resolver` made every surface read the resolved root (`OpenSpecRootResolver`, a cached snapshot with `source`, `storeId`, `problem` and `warning`). The user can't see where that root is, though.

- **The tree root is a plain string.** `SpecTreeModel.buildModel` creates `new DefaultMutableTreeNode("OpenSpec")`. The tree shows its root (`setRootVisible(true)`). `SpecTreeCellRenderer` sets each node's icon and tooltip, but only for `TreeNodeData` nodes (`setToolTipText(data.tooltip())`).
- **Code identifies the root by its string.** `OpenSpecToolWindowPanel` double-click handling treats a hint whose parent's user object equals `"OpenSpec"` as the not-set-up hint, and runs Initialize. `TreeRefreshStateTest` asserts the root is that string.
- **Initialize can run over a store today.** `store-root-resolver` made the root-level hint carry a store problem, so double-clicking it also runs Initialize. That would create a local planning tree that overrides the store.
- **Store files trip the platform's non-project write protection.** Editing a file outside the project's content runs into `NonProjectFileWritingAccessProvider`, which asks to unlock it.
- **There is no editor banner in the plugin today.**
- **The ignored-pointer warning is only logged.** The resolver keeps the CLI's warning on the snapshot and writes it to the log.

Upstream facts (checked for `store-root-resolver`):
- The CLI prints `Using OpenSpec root: demo (<path>)` when a store resolves. Its sources are `nearest`, `declared` and `global_default`.
- A `store:` key next to a real planning tree is ignored, with a stderr warning.
- Showing one root with a provenance label is on-model. Showing a local tree and a store tree together is not.

## Goals / Non-Goals

**Goals:**
- A user can always tell where the specs they see and edit live.
- Store files edit as smoothly as project files, with the version-control caveat stated where it matters.
- An ignored pointer is visible, not silent.

**Non-Goals:**
- Committing the store, showing its version-control state, or attaching it to the project. That last one would turn its inspections off, as library roots do.
- A plugin-side store picker, and new Settings or status-bar surfaces.
- Repeating balloons for a normal, working setup.

## Decisions

### D1. One wording helper

`RootProvenance` in the `root` package is a pure helper over `RootResolution` plus the project's base path. It builds:
- the tree root label;
- the tooltip;
- the ignored-pointer hint and notification text;
- the banner text;
- the problem title.

Words:
- `declared` is the CLI's own word for a pointer.
- `global default` for the CLI's `global_default` source. It is upstream's own user-facing phrase ("clear the stale global default"); "machine" appears only in upstream code comments.
- `parent folder` covers an ancestor root.

A root inside the project gets no label.

**Why:** five surfaces would otherwise drift. A pure helper is unit-testable without a platform.

### D2. The tree root carries the provenance

The root becomes a `TreeNodeData` of a new `ROOT` type, labelled and tooltipped by `RootProvenance`:
- The renderer draws it with no icon, like a hint, and its tooltip comes from the renderer's existing `setToolTipText(data.tooltip())`.
- `iconForType` gains a `ROOT` case.
- Its path element id is the fixed `"root"`, so expansion state survives a label change. The `default` branch would derive it from the label.
- Code that found the root by its string now checks the `ROOT` type, and `TreeRefreshStateTest` asserts the type.

The tree rebuilds on `OpenSpecRootResolver.TOPIC` today, so the label follows the root with no new wiring. No layout changes: the root row already exists.

**Alternative rejected:** a header line above the tree. It is a new layout element, and it doubles the toolbar's height on narrow tool windows.

### D3. The ignored pointer as a hint node and a one-time notification

When `snapshot.warning() != null`, a `HINT` node under the root reads: "config.yaml names store 'demo', but this project has its own openspec/ tree, so the store is ignored."

On the transition into that state (the previous snapshot had no warning), one `OpenSpec.System` notification says the same. The resolver already compares old and new snapshots before publishing, so it is the place to detect the transition.

The text is built from the snapshot, never parsed from CLI output.

### D3a. Only the not-set-up hint initializes

Root-level hints get a kind: `NOT_SET_UP`, `STORE_PROBLEM` or `IGNORED_POINTER`. It is carried on `TreeNodeData`, for example as the hint's `artifactId` slot, or as a small field.

Double-click runs Initialize only for `NOT_SET_UP`. The other two do nothing on activation; their text already says what to fix.

This also fixes the behavior `store-root-resolver` shipped unreleased.

### D4. Writable store files: `NonProjectFileWritingAccessExtension`

Register `nonProjectFileWritingAccessExtension`. Its `isWritable(file)` returns true when `OpenSpecRootResolver.getServiceIfCreated(project)` has a snapshot whose `openspecDir` is an ancestor of the file and lies outside the project.

It checks ancestry in memory only. It never reads the disk, and it never creates the service.

Files outside both the project and the resolved root keep the platform's prompt.

**Checked on the 2024.2 build:**
- `nonProjectFileWritingAccessExtension` is a project-area (`IDEA_PROJECT`) dynamic extension point.
- The interface is `com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension` with a default `isWritable(VirtualFile)` (and `isNotWritable`).
- A project-area implementation can take the `Project` in its constructor.

**Still to confirm with a test:** platform tests need `NonProjectFileWritingAccessProvider.enableChecksInTests` to see the prompt at all.

### D5. The banner: `EditorNotificationProvider`

Register `editorNotificationProvider`, and make it `DumbAware`. For a file under a resolved root outside the project, it returns an `EditorNotificationPanel` with `Status.Info`:
- the text from `RootProvenance`;
- a "Don't show again for this project" link, stored in `PropertiesComponent`.

The decision is ancestry against the snapshot only.

When the root changes, the resolver's listener calls `EditorNotifications.updateAll()`.

**Checked on the 2024.2 build:**
- `editorNotificationProvider` is a project-area dynamic extension point.
- `EditorNotificationProvider` is `PossiblyDumbAware`, with `collectNotificationData(Project, VirtualFile)` returning a function from `FileEditor` to the panel.
- `EditorNotificationPanel.Status.Info` exists.

**Still to confirm with a test:** the platform consults providers for a file outside content roots.

### D6. Problems name the store

`GettingStartedPanel`'s problem card title and `SpecTreeModel.STORE_PROBLEM_PREFIX` take the store id when the problem names one. The id is exposed on the problem alongside `code`, `message` and `fix`. The CLI's message and fix stay word for word, so the contract tests are unchanged.

### D7. Tests

- **`RootProvenanceTest` (plain):** every source, a root inside or outside the project, a warning, and problems with and without an id.
- **Platform tests on real temp directories**, as in `StoreRootPlatformTest`:
  - the tree root's label and tooltip for each source, and expansion kept when the label changes;
  - double-clicking each root-level hint kind: only the not-set-up hint initializes, and the store-problem hint leaves no local `openspec/specs` or `changes`;
  - the ignored-pointer hint, with the notification fired once across refreshes;
  - `isWritable`: true for a store file, false for an unrelated non-project file, false when the service hasn't been created;
  - the banner shown and hidden, and its dismissal;
  - `EditorNotifications` refreshed on a root change.
- **The store uiSmoke journey** also asserts the root label text and the banner on an opened store file.
- **Plain tests run alone.** Each new plain test class runs alone before push, as every class does on CI.

## Risks / Trade-offs

- **The banner provider could be slow.** It runs for every opened file. Mitigation: the snapshot check is O(1). It returns early for local roots and for files under the project.
- **Suppressing the write prompt removes a safety net.** Mitigation: only files under the resolved root are affected, and the banner states the version-control caveat in its place.
- **The wording is a product choice.** It follows upstream's output text, lives in one helper, and the Marketplace screenshot is re-read at capture time.

## Migration Plan

None. Projects with their own `openspec/` see no change.

## Open Questions

None blocking. The extension points are confirmed on 2024.2; the two remaining checks in D4 and D5 are tests.
