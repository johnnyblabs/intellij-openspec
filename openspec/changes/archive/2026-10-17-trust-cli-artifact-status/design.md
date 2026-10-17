## Context

`ArtifactOrchestrationService.getArtifactStatus` parses `openspec status --change <name> --json`, then `applyScaffoldingOverrides`:
- For each DONE artifact with a single-file output, it asks `ScaffoldingDetectionService.isScaffolding`: fewer than 20 characters after stripping headings, HTML comments, and placeholder lines copied from the plugin's own tasks template.
- It downgrades flagged artifacts to READY, or to BLOCKED by any earlier flagged artifact in list order.
- It recomputes `isComplete`.

Every DAG consumer inherits the result: the panel chips and `allArtifactsComplete`, Apply, Continue, Verify completeness, Bulk Archive, the tree's complete marker, the schema context, and Generate All / Fast-Forward next-artifact selection.

An openspec-guru ruling against the 1.14.3 source and real runs found the following:
- **Done-ness upstream** is file existence only (`artifact-graph/state.js`).
- **`openspec new change`** writes only `.openspec.yaml`.
- **The plugin's templates read as complete:** planted, they make `status` report every artifact done and `isComplete: true`.
- **Upstream's content checks live elsewhere:** `validate` errors (an empty delta), `instructions apply` reporting `blocked` for a `tasks.md` with no tasks, and archive's validation.
- **The ruling:** the override is PARTIAL. Changing status is off-model; a non-blocking hint is on-model.

## Goals / Non-Goals

**Goals:** the plugin's view of a change's artifacts is exactly the CLI's; the plugin never creates files upstream wouldn't, which would read as done; template-looking files are still pointed out, without blocking.

**Non-Goals:**
- Changing the Create Delta Spec scaffold.
- Following `instructions apply`'s blocked state in the Apply gate (a possible follow-up).
- Upstream's informational short-Why notice.

## Decisions

### D1. Remove the override; keep the detector for a hint

`getArtifactStatus` returns the parsed DAG unchanged and caches it. A new `ArtifactOrchestrationService.templateLookingArtifacts(changeName, dag)` returns the ids of DONE single-file artifacts that `ScaffoldingDetectionService` flags. It is computed off the UI thread with the DAG and carried to the chips. Nothing else consults it. The detector's rule changes from "fewer than 20 characters left" to "nothing left": after stripping headings, HTML-comment guidance and the known placeholder task lines, any remaining text means someone wrote the file. Every template it needs to recognise (the old design and tasks templates, the delta scaffold) strips to nothing, while the sandbox's real-but-tiny files ("Use a set.", one ticked task) were mislabelled by the length rule.

### D2. Propose writes the upstream set

`ScaffoldingService.createChange` writes `.openspec.yaml`, `proposal.md` (from the dialog's name, why and what changes) and `specs/`. The `design.md`/`tasks.md` template writes are removed. If `TemplateProvider.designTemplate`/`tasksTemplate` lose their last caller, they are deleted, and the detector's placeholder lines are kept because existing changes may still contain them.

### D3. The hint on the chip

Settled with plugin-ui-specialist. A DONE chip whose artifact is in the template set keeps its check mark and click (opens the file), and:
- **Label:** it reads `design · template`, a text cue so colour isn't the only signal.
- **Colour:** the name uses the theme's secondary-text colour (`JBUI.CurrentTheme.ContextHelp.FOREGROUND`) instead of the done green.
- **Tooltip:** "Click to open · Right-click for options", then "Looks like an unfilled template · Right-click → Regenerate to replace it".
- **Accessibility:** the chip's accessible description says the same.

The existing Regenerate item is the fix, and no action is enabled or disabled by the hint. It stays chip-only: a tree badge or a Verify finding would put the heuristic back on surfaces the CLI owns. The hint set lives in one panel field read by both chip renders, including the re-render during generation.

### D4. Contract test the pass-through

Capture `openspec status --change c --json` from 1.14.3 for a change holding the plugin's design/tasks templates (all four done, `isComplete: true`). Assert that `getArtifactStatus`'s DAG equals the parsed capture, and that `templateLookingArtifacts` names design and tasks.

## Risks / Trade-offs

- **Existing changes with plugin templates now show as complete,** so Apply, Verify and Archive enable and Generate All skips those artifacts. → The hint names the file, Regenerate replaces it, and upstream's own checks still apply: `validate` flags an empty delta, `instructions apply` blocks an empty task list, and archive validates. That is the same treatment as a CLI user's change.
- **Generate All / Fast-Forward on a change made by the old Propose** no longer regenerates template design/tasks. → New changes have no templates (D2). For old ones, Regenerate is one click per artifact.
- **The chip styling is new UI.** → It's a minimal tooltip and colour change on an existing control; no new action.

## Migration Plan

Code-only. Changes on disk are not rewritten. To roll back, revert the commit.
