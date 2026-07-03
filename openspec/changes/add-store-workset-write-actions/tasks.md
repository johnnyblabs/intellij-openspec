## 1. Precondition — verify the CLI write surface (do first)

- [ ] 1.1 Run `openspec store setup <id> --path <p> --json` against the real 1.5.0 CLI in a **non-TTY / non-interactive** context (isolated `XDG_DATA_HOME`). Confirm it does not block on an interactive prompt; if it does, identify the non-interactive flag the CLI provides. Capture the success `--json` output (`{store, registry, git, created_files, status}`) as a contract fixture.
- [ ] 1.2 Capture the `store_setup_path_required` error shape (run `store setup <id>` with `--path` omitted) and the uniform `status:[{severity,code,message,target,fix}]` array as fixtures under `src/test/resources/fixtures/cli/`. Sanitize machine-specific paths.
- [ ] 1.3 Capture `workset create … --json`, `store register`/`unregister`/`remove`, `workset open`/`remove`, and `doctor --store <id> --json` outputs as fixtures. If 1.1 shows `store setup` blocks with no non-interactive escape, HOLD the New Store write flow and record the blocker in the change before proceeding.

## 2. Coordination service — store/workset write methods

- [ ] 2.1 Add `setupStore(id, path)` running `openspec store setup <id> --path <p> --json`, parsing `{store, registry, git, created_files, status}`; return a result carrying the created store root, `created_files`, and the parsed `status[]`.
- [ ] 2.2 Add `registerStore(path)` (`store register <path>`), `unregisterStore(id)` (`store unregister <id>`), and `removeStore(id)` (`store remove <id>`) — flag `removeStore` as destructive in its result contract.
- [ ] 2.3 Add `createWorkset(name, members)` running `workset create <name> --member name=path …` (one `--member` per row); add `openWorkset(name)` resolving the member folder list and `removeWorkset(name)` (`workset remove <name>`).
- [ ] 2.4 Add `storeDoctor(id)` running `doctor --store <id> --json`, parsing the uniform `status:[{severity,code,message,target,fix}]` array; return the highest-severity entry with its `fix` string intact.
- [ ] 2.5 Parse the uniform `status[]` on every write result; expose `fix` verbatim as the suggested action and NEVER surface raw stderr to the user. Recognize `store_setup_path_required` and map it to a field-level message. All methods off-EDT; retire the 1.4 `setupContextStore`/`createInitiative`/`setupWorkspace` methods.
- [ ] 2.6 Gate every write method on FULL tier AND CLI ≥ 1.5.0; below the bar return a disabled/guidance result rather than shelling out.

## 3. Dialogs (DialogWrapper)

- [ ] 3.1 Add `NewStoreDialog` (`DialogWrapper`): a store-id `JBTextField` and a folder `TextFieldWithBrowseButton` bound to `FileChooserDescriptorFactory.createSingleFolderDescriptor()`. `doValidate()` blocks OK on a blank id or a blank/non-existent folder (path is mandatory — `store setup` requires `--path`).
- [ ] 3.2 Add `NewWorksetDialog` (`DialogWrapper`): a name `JBTextField` plus an add/remove member list where each row is a name field + a folder picker. `doValidate()` blocks OK on a blank name or any incomplete member row.
- [ ] 3.3 Both dialogs return typed value objects (store id+path; workset name+members) — no CLI calls inside the dialog; the panel runs the write off-EDT after OK.

## 4. Panel toolbar and context menu (AnAction / ActionToolbar)

- [ ] 4.1 Replace the `JButton`/`FlowLayout` toolbar with an `ActionToolbar` built from an `AnAction` group (icons + keyboard). Add a tree `PopupHandler` for right-click context actions.
- [ ] 4.2 Implement store actions: New Store, Register Existing Store, and per-store Doctor / Open Root / Unregister / Remove. Implement workset actions: New Workset, Open, Remove.
- [ ] 4.3 Each `AnAction.update()` reads cached tier + tree selection only — no CLI or IO on the EDT — and disables (or hides) the action outside FULL tier / CLI ≥ 1.5.0, and when the selection doesn't match the action's target type.
- [ ] 4.4 Guard destructive actions with `Messages.showYesNoDialog`: store Remove names that it DELETES local files; workset Remove states that member folders are NOT touched.

## 5. workset open — multi-folder / attached-project integration

- [ ] 5.1 On Open, resolve the workset's member folders off-EDT, then show an explicit "opens N folders" confirmation. Never auto-open a workset on tab load.
- [ ] 5.2 Open the first member in the current window and offer the remaining members as attached directories / additional projects via the platform's own multi-folder API. Do NOT use the `--code-workspace` flag. Refresh VFS off-EDT; wrap platform mutations in a `WriteAction` where required.

## 6. Health strip (doctor-driven)

- [ ] 6.1 Add a health-strip component at the top of the panel (an `EditorNotificationPanel`-style row / `JBLabel` with a severity icon) that renders the highest-severity `status[]` entry from `doctor`.
- [ ] 6.2 Expose the entry's `fix` string verbatim as an inline `HyperlinkLabel` action; hide the strip when there are no non-info status entries. Refresh the strip on reload and after each write, off-EDT.

## 7. Documentation

- [ ] 7.1 Update README with the store/workset write actions and the health strip (vendor-neutral).
- [ ] 7.2 Update CHANGELOG under the unreleased section.
- [ ] 7.3 Update `docs/feature-reference.md` (and any wiki source) with the new actions, destructive-remove confirmations, and `workset open` behavior.
- [ ] 7.4 Update the coverage matrix `docs/openspec-support.md`: update the `store`/`workset` rows to reflect Full-tier write actions (setup / register / create / open / remove) and the doctor-driven health strip, so the rows no longer read as read-only. Keep vendor-neutral.

## Tests

- [ ] T.1 Contract tests parsing the captured 1.5.0 fixtures from task 1: `store setup --json` success (`store`/`registry`/`git`/`created_files`/`status`), the `store_setup_path_required` error, `workset create --json`, and `doctor --store --json` (uniform `status[]` with `fix`). Each test fails if the corresponding parser is broken.
- [ ] T.2 Service tests: write methods gated off below FULL tier / CLI < 1.5.0 (return guidance, no shell-out); `status[].fix` surfaced verbatim and raw stderr never returned; `removeStore` flagged destructive.
- [ ] T.3 Dialog validation tests: `NewStoreDialog.doValidate()` blocks a blank id or blank/non-existent path; `NewWorksetDialog.doValidate()` blocks a blank name or incomplete member row.
- [ ] T.4 Action enablement tests: store/workset write `AnAction`s disabled outside FULL tier and CLI < 1.5.0 and on mismatched selection; `update()` performs no CLI/IO; destructive actions require confirmation.
- [ ] T.5 Health-strip test: highest-severity `status[]` entry rendered with its `fix` as the inline action; strip hidden when only info-level entries exist.
