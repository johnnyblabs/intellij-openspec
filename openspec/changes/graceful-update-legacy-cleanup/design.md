# Design — Graceful legacy-file cleanup in the Update action

## Context

Observed live on CLI 1.4.1 from the plugin's Update action (output shape, abridged):

```
$ openspec update

Upgrading to the new OpenSpec
...
Files to remove
No user content to preserve:
  • .junie/commands/opsx-apply.md
  • .junie/commands/opsx-archive.md
  ...
⚠ Run with --force to auto-cleanup legacy files, or run interactively.

✓ All 5 tool(s) up to date (v1.4.1)
Use --force to refresh files anyway.
(exit 0)
```

Three facts shape the design: the CLI **exits 0** (so exit-code handling can't catch this), it **enumerates the exact safe-to-remove files** and certifies "No user content to preserve" (so the removal decision is already made by the authority — the CLI), and its two suggested remediations (interactive run, `--force`) are both unreachable from a non-interactive read-only console.

## Goals / Non-Goals

**Goals:**
- Turn the silent-incomplete Update into a completable, consented flow.
- Surgical cleanup: exactly the CLI-listed files, deleted IDE-natively (undoable, Local-History-covered), never via `--force`.
- Preserve every escape hatch: per-file opt-out, terminal handoff, dismiss-without-nag.

**Non-Goals:**
- No `--force` invocation by the plugin, ever — its bundled "refresh all instruction files" half is the documented customization-clobber risk and stays a user-typed terminal decision.
- No plugin-side judgment of what counts as "legacy" — the plugin renders and executes the CLI's list, nothing else.
- No handling of other interactive `update` prompts (none are currently known on the supported CLI range; new ones get their own analysis when they appear).

## Decisions

1. **Detection is a pure output parser fed by the existing result path.** `UpdateOutputParser.parseLegacyCleanup(stdout)` returns the pending file list (or empty). Recognition keys on the stable structural markers ("Files to remove", the bulleted paths, the `--force` guidance line) rather than the full prose, and is contract-tested against a fixture captured from the real CLI (the live output above, re-captured verbatim at implementation time in a scratch project with seeded legacy files under an isolated environment). Rationale: exit 0 forces text recognition; the repo's contract-testing rule forbids hand-guessing the shape.
2. **Consent UI is a dialog launched from an actionable notification** (not a modal ambush): the Update completion notification gains a "Review legacy cleanup…" action opening a dialog with the file checklist (all checked), each entry a link that opens the file, and the CLI's own "no user content to preserve" statement quoted so the user sees whose judgment this is. Buttons: **Remove selected**, **Run in terminal**, **Not now**.
3. **Deletion is IDE-native and scope-locked.** Checked files are deleted via VFS inside a single `WriteCommandAction` (one undo step; Local History records each file). The deletion set is the intersection of (CLI-listed paths) ∩ (user-checked) ∩ (exists on disk, resolved against the project base dir — paths escaping the project root are discarded). After deletion the action re-runs plain `openspec update` and reports the now-clean result.
4. **Dismissal memory is keyed on the file set.** "Not now" stores a hash of the sorted pending path list in project-level plugin state; the notice is suppressed while the pending set is unchanged and reappears the moment the CLI reports a different set. Rationale: no permanent mute (the state is real and eventually wants resolving), no per-Update nagging either.
5. **Terminal handoff reuses `OpenSpecTerminalLauncher`** to open the project terminal with the `openspec update` command pre-typed (not executed), so the user drives the CLI's interactive flow themselves.

## Risks / Trade-offs

- [Upstream rewords the migration text and the parser goes blind] → the flow degrades to exactly today's behavior (output visible in console, nothing detected); the contract fixture makes the breakage loud in CI the next time it's re-captured, and recognition keys on the most stable structural lines.
- [Paths in the list could be absolute or contain surprises] → scope lock in decision 3 (project-root containment + existence check) bounds the blast radius to files the CLI listed inside the project.
- [Deleting tracked files surprises a user who didn't read] → the dialog is explicit, per-file, quotes the CLI's certification, and the deletion is one undo step; git/Local History both cover recovery.

## Migration Plan

Additive; no state migration. Ships in the next minor release.

## Open Questions

- Whether the CLI localizes or rewords the migration block across 1.4.x/1.5.x/1.6.x — the implementation captures fixtures from the newest available CLI as well as 1.4.1 and keys recognition on the most stable markers found across them.
