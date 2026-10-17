## Context

See proposal.md — Why. Current state in the code:

- **Change node state.** `SpecTreeModel.changeNodeType(dag)` returns `CHANGE_DONE` when the CLI artifact DAG reports `isComplete()`, and `CHANGE` otherwise (including when the DAG is null, i.e. no CLI).
- **Task counts.** `readTaskCounts` returns `[completed, total]` from `ApplyPromptBuilder.countTasks`, or `null` when there is no `tasks.md` or zero tasks. `countTasks` delegates to `TaskLines`, a contract-tested port of the client's task-line grammar, so these counts equal `openspec list --json`.
- **Rendering.** `SpecTreeCellRenderer` maps types to icons in `iconForType`; `CHANGE_DONE_ICON` is the change icon with a green-dot badge in the SE corner. `changeFragments` emits the name plus a `GRAYED` ` X/Y` count, deliberately never greened. Both methods are pure and unit-tested headless.
- **Other switches.** `CHANGE`/`CHANGE_DONE` are also matched in `SpecTreeModel`'s change-node filter, `OpenSpecToolWindowPanel`'s selection switch, and `SpecPreviewRenderer`.
- **Verify.** Verify already has a "Ready to archive" state that also requires validation to pass. The workflow panel's Archive affordance uses a check icon.

## Goals / Non-Goals

**Goals:**
- One glance tells an archive-ready change apart from an apply-ready one, without color and without hovering.
- Pure routing and fragment logic that is unit-testable headless, and that fails if either half of the condition is dropped.

**Non-Goals:**
- Changing the workflow panel, Bulk Archive (its row preselection), Verify, or notifications.
- Running validation in the tree. Verify stays the authority on whether archiving would succeed.
- Showing archive-ready without the CLI's artifact signal.
- **Noted as a follow-up, not fixed here:** the workflow panel's Archive icon button is enabled only when artifacts and tasks are complete, which acts as a gate, while the Verify dialog offers "Archive anyway" and the CLI's `--yes` bypasses the task warning. That inconsistency with the "recommendation, never a gate" ruling deserves its own change.

## Decisions

**1. A third node type that replaces the dot, rather than a second badge stacked beside it.** Add `CHANGE_ARCHIVE_READY` to `TreeNodeType`. `changeNodeType(dag, taskCounts)` returns it when `dag != null && dag.isComplete()` and `taskCounts != null && taskCounts[1] > 0 && taskCounts[0] == taskCounts[1]`. It returns `CHANGE_DONE` when only the DAG half holds, and `CHANGE` otherwise. The method stays static and pure. *Alternative considered:* stacking a check beside the dot. Rejected: two badges are unreadable at 16 px, and they lose the ordering of the stages.

**2. A check-mark badge, distinguished by shape.** `CHANGE_ARCHIVE_READY_ICON = badged(CHANGE_ICON, <small check icon>)`. A check reads as "finished" against the dot's "ready", and it echoes the workflow panel's Archive check. The badge must be a small existing platform icon available on 2024.2 (sinceBuild 242). The leading candidate is `AllIcons.General.GreenCheckmark`; the 16 px `AllIcons.Actions.Checked` would swamp the corner. Confirm in the IDE and with `verifyPlugin`. *Alternative considered:* greening the `N/N` count. Rejected: color-only, and the visual-hierarchy scenario forbids coloring the count.

**3. A label fragment that says "all tasks done", not "ready to archive".** `changeFragments` appends ` · all tasks done` after the dimmed count, only for `CHANGE_ARCHIVE_READY`. It uses a theme-correct success foreground (`JBColor.namedColor("Label.successForeground", <fallback>)` if it resolves on 242; otherwise a `JBColor` pair like the existing `DONE_COLOR`), cached as a static `SimpleTextAttributes`. The words carry the meaning, so the cue never depends on color. "Ready to archive" stays out of the label because Verify uses that phrase for a state that also requires validation, and the tree must never contradict Verify. `changeFragments` currently takes `ChangeLabelParts`; it needs the node type (or an archive-ready flag) passed in. Keep the method pure.

**4. Tooltip as a recommendation, one fact per line.** The change tooltip is a multi-line HTML tooltip:
- the status in bold first ("**All tasks done** — ready to archive (suggestion)" or "**Apply-ready** — all artifacts complete");
- the `X/Y tasks` count;
- for archive-ready only, an italic hint: "Run Verify to confirm — archiving may still be blocked by validation.";
- the change path last, in small text, **relative to the project root** when the change is under it (absolute otherwise, e.g. a store), and XML-escaped.

A change with no status or count shows just its path. The wording avoids "safe", "will" and "guaranteed". *Revised during the sandbox walkthrough:* the first version joined everything into one long " — "-separated line ending in the absolute path, which wrapped badly.

**5. Every change-type switch learns the new constant.** `SpecTreeModel`'s filter, `OpenSpecToolWindowPanel`, `SpecPreviewRenderer` and the renderer's `customizeCellRenderer` treat `CHANGE_ARCHIVE_READY` exactly like `CHANGE`/`CHANGE_DONE` (selection, preview, fragment rendering). A missed switch would silently break selection or preview for archive-ready changes, so each one gets a test or is covered by an exhaustive switch.

## Risks / Trade-offs

- **Risk: users read the check as "archiving will succeed".** → **Mitigation:** the label says "all tasks done", the tooltip says "suggestion" and points to Verify, and the walkthrough includes a broken-delta case where the tree shows the check while Verify reports validation failed.
- **Risk: the badge icon doesn't exist, or looks wrong, on 2024.2.** → **Mitigation:** use only long-standing `AllIcons` constants, run `verifyPlugin` against 242/243/251/252, and check light and dark themes in the sandbox.
- **Risk: a missed node-type switch** (preview or selection silently stops working for archive-ready changes). → **Mitigation:** grep every `CHANGE_DONE` reference and cover each one. Make switches exhaustive where Java allows.
- **Risk: the counts disagree with the CLI.** → **Mitigation:** none needed here. The counts come from `TaskLines`, which is already contract-tested against captured `openspec list --json`.

## Migration Plan

Additive, UI-only. No persisted state. Rollback means reverting the commit.
