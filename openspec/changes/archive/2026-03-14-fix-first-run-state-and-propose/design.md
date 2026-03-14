## Context

`detectState()` loops over `changesDir.getChildren()` looking for non-archive directories. If no active changes exist but archives do, it returns `NO_CHANGES` — showing the onboarding card on a project that's clearly been used before.

The wizard's done step sets `openProposeOnClose = true` then opens `ProposeChangeDialog` with `dialog.show()` (fire-and-forget). The dialog collects input but nobody calls `createChange()` with it.

## Goals / Non-Goals

**Goals:**
- Projects with archived changes skip the "first change" card
- Wizard's propose button actually creates the change on disk

**Non-Goals:**
- Redesigning the getting started flow

## Decisions

### Check for archives in detectState()

Add a check: if `changesDir` has an `archive` subdirectory with any children, return `READY`. An archived change means the project has been actively used.

### Invoke OpenSpec.Propose action from wizard instead of raw dialog

Replace the `ProposeChangeDialog.show()` call with `ActionManager.getAction("OpenSpec.Propose")` + `ActionUtil.invokeAction()`. This runs the full action including `createChangeBuiltIn()`, `refreshToolWindow()`, and `autoFocusChange()`. The wizard already closes via `doOKAction()` before the propose runs, so the action fires after the dialog is dismissed.

## Risks / Trade-offs

- **[None]** Both are straightforward bug fixes.
