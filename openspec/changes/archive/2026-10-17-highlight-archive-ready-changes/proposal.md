## Why

In a large Changes tree, finding the changes whose work is finished means reading every task count. A change at `25/25` looks the same as one at `3/25`: both show the same apply-ready badge, and the count is deliberately dimmed. A user asked for changes that have all tasks done and are ready to archive to stand out at a glance, to cut that scanning.

This is on-model. The highlighted state combines two signals the tree already shows: artifact completion, reported by the OpenSpec CLI, and task counts, counted with the CLI's own task-line grammar. It is the same condition the CLI checks when `openspec archive` warns about open tasks. It is a **recommendation**, never a gate: the CLI's archive only warns about open tasks, and archiving can still fail for other reasons such as spec validation.

Tracked in the linked tracker entry.

## What Changes

- **A third change-node state, archive-ready.** It shows when every artifact is complete **and** every task is done (`N/N`, with at least one task). Its badge is a check mark, which **replaces** the apply-ready dot, so a change node shows one stage at a time:
  - no badge: artifacts incomplete
  - dot: apply-ready
  - check: all tasks done

  The dot and the check differ in shape, so the stages can be told apart without relying on color.
- **A label cue.** A ` · all tasks done` fragment follows the still-dimmed `X/Y` count, so the state scans as text. It reads "all tasks done", not "ready to archive", because Verify's "Ready to archive" also requires validation to pass, which the tree does not run.
- **A recommendation tooltip.** It names the state first, frames it as a suggestion, and points to Verify as the authority (archive may still be blocked by validation).
- **Without the CLI, neither state appears**, the same honest degradation as the existing apply-ready badge. The artifact half is never guessed.
- **Demo seed data** for a sandbox walkthrough: an apply-ready change and an archive-ready change.
- **Not changed:** the workflow panel, Bulk Archive, Verify, notifications. No new state is written anywhere.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `tree-view`: three requirements change.
  - "Artifact status badge overlays": the change-node badge vocabulary gains the archive-ready status.
  - "Change node status rollup": the archive-ready state, as a combination of client-owned signals.
  - "Changes tree display": the visual-hierarchy scenario still keeps the count dimmed at `N/N`, and archive-ready is conveyed by its own badge and text fragment.

## Impact

- **Code:**
  - `SpecTreeModel`: a new node type, the routing in `changeNodeType`, and the tooltip.
  - `SpecTreeCellRenderer`: the badge icon, `iconForType`, and the label fragment in `changeFragments`.
  - Every switch or filter over the change node types: `SpecTreeModel`'s filter, `OpenSpecToolWindowPanel`, and `SpecPreviewRenderer`.
- **Tests:** `SpecTreeModelLabelTest` and `SpecTreeCellRendererTest`.
- **Scripts:** `scripts/seed-lifecycle-demo.sh` (two extra demo changes).
- **Docs (vendor-neutral):** README, `docs/feature-reference.md`, CHANGELOG.
- **Platforms/CLI:** no change to IntelliJ 2024.2+ compatibility or the supported CLI range. Only existing platform icon and color APIs are used, checked by `verifyPlugin`.
