# Design

## Context

`OpenSpec.Continue` (`OpenSpecContinueAction`, workflow `continue`) is registered only in `OpenSpec.MainMenu`. The active change's right-click menu (`OpenSpecToolWindowPanel.contextMenuFor`) offers Explore This Change, Revise Change, then Apply and Archive. The workflow panel's icon bar has Revise, Apply, Verify, Sync Specs and Archive; its `pipeline-interaction` requirement still lists a Compliance button and an overflow menu the panel no longer has. Getting Started is shown only before a project is set up, so the workflow panel's "No changes yet" card is the empty state a set-up project sees.

## Goals / Non-Goals

**Goals:**
- Continue is offered where Revise, Apply and Archive are.
- A custom-profile user learns the other workflows are in the OpenSpec menu.

**Non-Goals:**
- An overflow menu, or changing the stale overflow requirement (separate).
- Changing what Continue does.

## Decisions

### D1. The right-click menu

Continue goes directly after Revise Change, above the separator before Apply. Its gating is the action's own (`OpenSpecBaseAction.update`): on a profile without `continue` it shows disabled with the "(custom)" suffix, as any gated action does.

### D2. The panel button

A `ChangeDataButton` with `AllIcons.Actions.Resume` (the action's icon), after Revise and before Apply, launched through the action as Revise is. It is **hidden** when the profile doesn't include `continue`: a permanently disabled button on the core profile would read as a fault. When shown, it is enabled while the change has a ready artifact. Tooltips: "Continue: <change>"; disabled, "Continue (all artifacts done)", or "Continue (no artifact is ready)". The profile is read on the refresh's pooled thread, since the profile service may run the CLI on first use.

### D3. Continue's change

`OpenSpecContinueAction` uses the change in `OpenSpecDataKeys.CHANGE_NAME` when it names an active change, and asks with `ChangePicker` only otherwise, as Revise does.

### D4. The custom-profile hint

The "No changes yet" card gains a muted line, "More workflows are in the OpenSpec menu.", shown only when the active profile is `custom` and includes a workflow outside the core set for the detected CLI. Its tooltip lists those workflows. The decision is a pure function of the profile name, its workflows and the core set.

## Risks / Trade-offs

- **A seventh icon on custom profiles.** Accepted: it appears only for users who chose `continue`.

## Migration Plan

None.

## Open Questions

None.
