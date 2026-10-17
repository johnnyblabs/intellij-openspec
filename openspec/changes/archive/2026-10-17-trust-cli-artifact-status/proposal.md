## Why

The plugin overrides the OpenSpec CLI's artifact status. When `openspec status` reports an artifact `done`, the plugin re-marks it READY or BLOCKED if the file looks like an unfilled template, defined as fewer than 20 characters of text besides headings, comments and a few known placeholder lines. That re-marking disables Apply, Verify and Archive. It also drives the pipeline chips and Generate All, with nothing in the UI saying why.

Upstream OpenSpec has no such state: an artifact is done when its file exists (`artifact-graph/state.js`), and `openspec new change` writes only `.openspec.yaml`. The override exists to cover for the plugin itself. Its built-in Propose writes template `design.md` and `tasks.md` files up front, which upstream never does. The size rule also catches short files someone wrote on purpose. A sandbox test found a change the CLI reported complete with Archive disabled, because its `design.md` was 10 characters.

Under the plugin's rule of mirroring the client, the override is invented state that contradicts the CLI. Upstream's real content checks are `validate` errors, `instructions apply` refusing an empty task list, and archive's validation, and the plugin already follows those.

Tracked in the linked tracker entry.

## What Changes

- **Trust `openspec status`.** The plugin uses the CLI's artifact status and `isComplete` unchanged. Pipeline chips, the Apply, Verify, Archive and Continue gates, Bulk Archive's completeness, the tree's complete marker and Generate All's next-artifact choice all read the CLI's answer.
- **Built-in Propose writes what `openspec new change` writes,** plus the proposal from the Propose dialog: `.openspec.yaml`, `proposal.md` and an empty `specs/` folder, with no template `design.md` or `tasks.md`. Design and tasks are generated or written next, and the CLI reports them ready until they exist.
- **Template detection becomes a hint.** A done artifact whose file still looks like an unfilled template keeps its DONE chip, which adds a "looks like an unfilled template" note to its tooltip and a muted style. The chip's existing Regenerate item replaces it. The hint never disables an action.
- **Unchanged:** the Create Delta Spec action still writes its scaffold with HTML-comment guidance, and the detector still recognises an unfilled one, now as the hint.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `validation`: scaffolding content detection marks a template-looking artifact with a hint and never changes its status.
- `workflow`:
  - Propose creates only what `openspec new change` creates, plus the proposal;
  - the pipeline chips show the CLI's status with the template hint;
  - an unfilled delta scaffold is detected as a hint.
- `verify-workflow`: Verify's completeness uses the CLI's status unchanged.

## Impact

- **Code:**
  - `ArtifactOrchestrationService`: removes `applyScaffoldingOverrides` and adds a template-hint query.
  - `ScaffoldingService.createChange`: stops writing `design.md` and `tasks.md`.
  - `WorkflowActionPanel` chips: the hint styling and tooltip.
  - `TemplateProvider`'s design and tasks templates, if they become unused.
- **Tests:**
  - a contract test that the DAG equals captured `openspec status --json` for a change with template-looking files;
  - Propose creating only the upstream set;
  - the chip hint;
  - rewritten versions of `ScaffoldingOverrideTest`/`ArtifactOrchestrationServiceTest` (removal, not deletion, of the override assertions).
- **Behaviour for existing changes:** changes made by older plugin versions may contain template `design.md`/`tasks.md`. They now show as done with the hint, and Regenerate replaces them.
- **Docs:** CHANGELOG, and `docs/feature-reference.md` (pipeline chips, Propose).
