## 1. Routing: the archive-ready state

- [x] 1.1 Add `CHANGE_ARCHIVE_READY` to `SpecTreeModel.TreeNodeType`, and change `changeNodeType(dag)` to `changeNodeType(dag, taskCounts)`: archive-ready when the DAG is complete and `taskCounts` is non-null with `total > 0 && completed == total`; done when only the DAG half holds; plain otherwise. Pass the already-read `taskCounts` from `buildChangesNode`. Extend `SpecTreeModelLabelTest`, using the same `ChangeArtifactDag` construction the existing routing tests use:
  - complete DAG + `3/3` → archive-ready
  - complete DAG + `2/3` → done
  - complete DAG + `null` counts (no tasks) → done
  - incomplete DAG + `3/3` → plain
  - null DAG (no CLI) + `3/3` → plain

  Verify the first and fourth cases fail if either half of the condition is removed.
- [x] 1.2 Extend `buildChangeTooltip` for archive-ready: lead with "All tasks done — ready to archive (suggestion)", keep the `X/Y tasks` part and the path, and add "Run Verify to confirm; archive may still be blocked by validation". Leave the apply-ready tooltip unchanged. Test that the archive-ready tooltip starts with the state, contains "suggestion" and "Verify", and contains none of "guarantee", "safe" or "will archive". Make the method package-private static if needed so it is testable headless.

## 2. Rendering: badge and label cue

- [x] 2.1 Add `CHANGE_ARCHIVE_READY_ICON = badged(CHANGE_ICON, <small check icon>)` and map it in `iconForType`. Choose a long-standing `AllIcons` check that reads at badge scale on 2024.2 (candidate `AllIcons.General.GreenCheckmark`). Extend `SpecTreeCellRendererTest`: the archive-ready icon is a `LayeredIcon` whose badge layer differs from `CHANGE_DONE_ICON`'s badge layer, and spec, requirement, delta-spec and config nodes are still never badged.
- [x] 2.2 Make `changeFragments` emit ` · all tasks done` after the dimmed count for archive-ready nodes only, with a cached theme-correct success `SimpleTextAttributes` (`JBColor.namedColor("Label.successForeground", fallback)` if it resolves on 242, otherwise a `JBColor` pair). The name stays `REGULAR` and the count stays `GRAYED`, including at `N/N`. Pass the node type or an archive-ready flag in, and keep the method pure. Tests:
  - archive-ready emits exactly name, count and fragment
  - done and plain nodes at `N/N` emit no fragment, and the count stays `GRAYED`
  - the fragment text contains "all tasks done" and not "ready to archive"
- [x] 2.3 Treat `CHANGE_ARCHIVE_READY` like `CHANGE`/`CHANGE_DONE` everywhere a change type is matched:
  - `SpecTreeCellRenderer.customizeCellRenderer`
  - `SpecTreeModel`'s change-node filter
  - `OpenSpecToolWindowPanel`'s selection switch
  - `SpecPreviewRenderer`

  Verify with `grep -rn "CHANGE_DONE" src/main/java` that every hit also handles the new type. Add a preview test (or extend an existing one) showing an archive-ready node previews like a change node.

## 3. Demo seed data

- [x] 3.1 Extend `scripts/seed-lifecycle-demo.sh` with `demo-apply-ready` (all artifacts present, valid deltas, tasks `1/3`) and `demo-archive-ready` (all artifacts present, valid deltas, tasks `3/3`). Verify on a fresh seed that the real CLI's `openspec list --json` reports `1/3` and `3/3`, and that `openspec status --change <name> --json` reports `isComplete: true` for both.

## 4. Documentation

- [x] 4.1 In `docs/feature-reference.md`, describe the three change-node states (no badge, apply-ready dot, archive-ready check plus "all tasks done") in the change-node section, framing archive-ready as a suggestion with Verify as the authority. Add a README line. Verify no remaining text says the done badge is the only change-node badge.
- [x] 4.2 Add a `## Unreleased` CHANGELOG `### Added` entry: changes with all artifacts complete and every task done now stand out in the Changes tree (check badge plus "all tasks done"), as a suggestion to archive (Verify confirms). Verify `DocumentationHygieneTest` is green.

## 5. Verify

- [x] 5.1 Run `./gradlew build` and confirm it is green (test plus JaCoCo floor).
- [x] 5.2 Run `./gradlew verifyPlugin` (the change adds `AllIcons`/`JBColor` references) and confirm it is Compatible on all target IDEs with no new deprecated or removed API usage.
- [x] 5.3 Sandbox walkthrough (`/lifecycle-testdrive`) on the seeded demo. Confirm:
  - **Row states:** `demo-add-farewell` has no badge; `demo-apply-ready` has the dot and `1/3` with no fragment; `demo-archive-ready` has the check, `3/3` and "· all tasks done".
  - **Tooltips:** each names its state, and the archive-ready one reads as a suggestion pointing to Verify.
  - **Live update:** ticking the last boxes in `demo-apply-ready/tasks.md` flips dot → check without a manual refresh; unticking one flips it back.
  - **Themes:** light and dark both stay legible, and the stages remain distinguishable by shape and text alone.
  - **Verify is the authority:** after breaking a delta in `demo-archive-ready`, the tree still shows the check while Verify reports validation failed.
  - **No CLI:** with the CLI path pointed at a nonexistent binary, neither badge shows and the counts still do.

  Record screenshots or notes for the external reply. Don't post the reply until the user approves.

  **Result (2026-10-17):** walked through in the sandbox on a fresh `--archive-ready-demo` seed. Verdict: "pretty good". The one issue found was the tooltip, a single long line with the absolute path; it was reworked to one fact per line with a project-relative path and re-checked ("looks better").
- [x] 5.4 Run `openspec validate highlight-archive-ready-changes --strict` and confirm it is clean.
