## Why

OpenSpec CLI 1.5.0 introduces **stores** and **worksets** (very early beta) as "a simplified
approach to organizing specs and changes, replacing the previous workspace and initiative model."
The companion change `add-store-workset-read-surface` gives the IDE a read-only listing of stores
and worksets sourced from `openspec store list --json` and `openspec workset list --json`. That
surface deliberately stops at reading: a user can *see* stores and worksets but must drop to the
terminal to create, register, open, or remove them.

This change closes the write gap. It replaces the coordination panel's legacy 1.4-era write flows —
which chain `Messages.showInputDialog` calls and drive the removed `workspace`/`initiative`/
`context-store` write commands — with proper dialogs and IDE-native actions that delegate to the
1.5.0 `store` and `workset` write surface. It also surfaces store health from `openspec doctor`
inline, so a broken store is visible and its remedy is one click away rather than buried in stderr.

The 1.5.0 store/workset surface is early beta with promised breaking changes, so every write action
is gated (Full tier **and** CLI ≥ 1.5.0), degrades to a disabled state with guidance below that bar,
and treats destructive operations with explicit confirmation. This is a deferred, gated follow-up to
the read surface — it depends on and builds directly on it (see the linked tracker entry).

## What Changes

- **Replace the chained-input write flows with `DialogWrapper`s.**
  - **New Store** — a dialog with a store-id field and a folder path picker
    (`TextFieldWithBrowseButton` bound to `FileChooserDescriptorFactory.createSingleFolderDescriptor`).
    A path is mandatory because 1.5.0 `store setup` **requires `--path`** (a BREAKING change vs 1.4:
    omitting it fails with `store_setup_path_required`). `doValidate()` blocks OK on a blank id or a
    blank/non-existent folder. Runs `openspec store setup <id> --path <p> --json`.
  - **New Workset** — a dialog with a name field and an add/remove member list, where each member is
    a name plus a folder (name field + folder picker per row). `doValidate()` blocks OK on a blank
    name or any incomplete member row. Runs `openspec workset create <name> --member name=path …`.
- **Move the panel toolbar from bare `JButton`/`FlowLayout` to an `ActionToolbar` of `AnAction`s**
  (icons, keyboard shortcuts, `update()`-driven enablement) plus a tree right-click `PopupHandler`.
  Actions: New Store, Register Existing Store (`store register <path>`); per-store Doctor
  (`doctor --store <id> --json`), Open Root, Unregister (`store unregister <id>`), and Remove
  (`store remove <id>` — **destructive: deletes local files**, so it is guarded by a
  `Messages.showYesNoDialog` that names the deletion); New Workset, Open (`workset open <name>`),
  and Remove (`workset remove <name>` — member folders are never touched, stated in the confirm).
- **Integrate `workset open` with IntelliJ's own multi-folder / attached-project model.** Resolve the
  workset's member folders, then open the first member in the current window and offer the remainder
  as attached directories / additional projects. Do **not** rely on the VSCode-flavored
  `--code-workspace` flag. Guard with an explicit "opens N folders" confirmation, and never auto-open
  a workset on tab load.
- **Add a `doctor`-driven health strip** at the top of the panel (an `EditorNotificationPanel`-style
  row / `JBLabel` with a severity icon). It renders the highest-severity entry from the uniform
  `status:[{severity,code,message,target,fix}]` array and exposes that entry's `fix` string verbatim
  as an inline `HyperlinkLabel` action. Suggested actions come from `fix`; raw stderr is never dumped
  to the user.
- **Gate and thread everything.** Write actions are enabled only at the **Full** tier **and** when the
  detected CLI is **≥ 1.5.0**; below either bar they are disabled with guidance. Threading discipline
  is preserved: CLI shell-outs run on a pooled thread, all UI updates go through `invokeLater`, VFS
  refresh for attached/opened folders happens off the EDT (with a `WriteAction` where the platform
  requires it), and every `AnAction.update()` reads cached tier/selection state only — no CLI or IO
  on the EDT.

> **Precondition / open risk — verify before implementing.** `store setup`'s behavior in a non-TTY /
> `--no-interactive` context is **UNVERIFIED**. If the command emits a blocking interactive prompt
> when run without a terminal, it would hang the pooled thread that shells out to it. A task in this
> change verifies `store setup` against the real 1.5.0 CLI in a non-interactive context (and captures
> the `--json` output as a contract fixture) **before** any write code is written; if it blocks, the
> New Store flow must pass whatever non-interactive flag the CLI provides, or the action is held back.

## Capabilities

### New Capabilities
- `store-workset-actions`: IDE write actions for the OpenSpec 1.5.0 store/workset surface —
  dialog-driven store setup/register and workset create, an `ActionToolbar` + tree-context menu of
  gated `AnAction`s (including guarded destructive remove), `workset open` mapped onto IntelliJ's
  multi-folder/attached-project model, and a `doctor`-driven health strip that surfaces the CLI's
  `status[].fix` remedies inline. Gated to the Full tier and CLI ≥ 1.5.0, off-EDT, degrading
  gracefully below that bar.

### Modified Capabilities
<!-- None. Read-only listing lives in `add-store-workset-read-surface`; this change adds only the write/health surface as a new capability. -->

## Impact

- **Code:** new `DialogWrapper`s (New Store, New Workset), an `ActionToolbar` + `AnAction` group and a
  `PopupHandler` replacing the `JButton`/`FlowLayout` toolbar in the coordination panel, a health-strip
  component, and store/workset write methods on the coordination service that parse the uniform
  `status[]`/`created_files[]` result and surface `fix` verbatim. The removed 1.4 write methods
  (`setupContextStore`/`createInitiative`/`setupWorkspace`) and their chained-input handlers are
  retired.
- **CLI contract:** relies on 1.5.0 `store setup <id> --path <p> --json`, `store register <path>`,
  `store unregister <id>`, `store remove <id>`, `workset create <name> --member name=path …`,
  `workset open <name>`, `workset remove <name>`, and `doctor --store <id> --json`. All are
  contract-tested against captured real 1.5.0 output under `src/test/resources/fixtures/cli/`,
  including the `store_setup_path_required` error shape.
- **Platform compatibility:** no change — continues to support the supported IntelliJ Platform range
  across the JetBrains IDE family; nothing is language- or IDE-specific. All CLI/IO runs off the EDT;
  UI updates via `invokeLater`; services registered as `projectService`.
- **Docs:** README, CHANGELOG, feature-reference, and the coverage matrix (`docs/openspec-support.md`)
  updated to describe the store/workset write actions, the destructive-remove confirmations,
  `workset open` behavior, and the health strip (vendor-neutral).
- **Tracker:** the linked issue.

### Out of scope (referenced by name)
- **Read-only listing** of stores and worksets — owned by `add-store-workset-read-surface`, which this
  change depends on. This change does not re-describe or re-implement the listing.
- **Cross-platform CI** for the store/workset commands — owned by `add-cross-platform-ci-matrix`.
- **`context` / `doctor` agent-brief output** (the `context --store`/`doctor --json` narrative surface)
  — deferred to a later change; this change consumes `doctor --store` only for the health strip.
