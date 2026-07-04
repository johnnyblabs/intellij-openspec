# Tasks — Feedback action + support-doc accuracy fixes

## 1. Feedback action

- [ ] 1.1 Feedback dialog (message field with non-empty validation, optional body area) + action class registered in `plugin.xml` (Tools → OpenSpec, tool-window overflow)
- [ ] 1.2 Delegate to `openspec feedback <message> [--body <body>]` on a background thread via the existing CLI runner; success/error notifications, stderr included on failure
- [ ] 1.3 Visibility predicate: hidden when no CLI is detected (reuse the existing availability check)
- [ ] 1.4 Tests: argument construction, empty-message rejection, failure-notification path, visibility gating

## 2. Support-doc accuracy fixes

- [ ] 2.1 `docs/openspec-support.md`: reclassify archive-change from `delegated` to `built-in` (code: `ChangeService` VFS move)
- [ ] 2.2 `docs/openspec-support.md`: correct the 1.4 context-store row to `setup`-only (no 1.4 `register` action in the plugin; register/unregister/remove exist only for 1.5 stores)
- [ ] 2.3 `docs/openspec-support.md`: replace the "`set` not yet confirmed upstream" caveat with the verified 1.4.1 fact (`set change --initiative/--store/--store-path/--json`)
- [ ] 2.4 `docs/cli-versions/1.4.md`: resolve §5 open questions with the verified 1.4.1 command surfaces (`set` confirmed; `context-store setup/register/unregister/remove/list/doctor`; `initiative create/show/list`; `workspace setup/list/link/relink/doctor/update/open`); update the `feedback` row when the action ships

## 3. Documentation of the new action

- [ ] 3.1 `docs/feature-reference.md` entry for Send OpenSpec Feedback; CHANGELOG entry under Unreleased; `docs/openspec-support.md` feedback row from "no surface yet" to supported
