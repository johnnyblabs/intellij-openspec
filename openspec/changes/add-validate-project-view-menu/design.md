## Context

`OpenSpecValidateAction` validates the whole project (built-in validator always + CLI `validate --all --json`, merged) in a `Task.Backgroundable`, reporting to the console. It's registered under the plugin's own `OpenSpec.MainMenu`. This change surfaces Validate in the standard **Project View** right-click menu, scoped to the clicked change/spec.

Two advisory passes settled the design. On-model: `openspec validate <id> --type change|spec` is a first-class single-target operation (`<id>` is a change or spec **directory name**, never a file path — the CLI rejects paths), and the built-in validator already has `validateChange(name)` and spec-level methods; archived changes and non-item files aren't single-validatable. Platform: reuse via subclassing, register a distinct action, gate visibility on `ActionUpdateThread.BGT` with the cheap `isUnderOpenSpec` path check, `anchor="last"` (never a relative anchor — multi-IDE trap).

## Goals / Non-Goals

**Goals:**
- A Project-View context-menu Validate, visible only under `openspec/`, scoped to the clicked change/spec.
- Reuse the existing built-in+CLI validate pipeline; no duplicated merge logic.
- Cheap, correct visibility gate; off-EDT validation.
- No per-file verdict; graceful fallback for non-item selections.

**Non-Goals:**
- No new validation logic, no new dependency, no since-build bump.
- No per-file or per-archived-change verdict.
- No change to the existing tool-window/menu Validate behavior.

## Decisions

**Decision 1 — New action subclassing `OpenSpecValidateAction`; not a `<reference>`.**
The base action's `update()` gates only on "is an OpenSpec project," so referencing it in `ProjectViewPopupMenu` would show it on every right-click. A new `OpenSpecValidateFromProjectViewAction extends OpenSpecValidateAction` gets a selection-aware `update()` while inheriting the background `actionPerformed`. Registered with `<add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>`, text "Validate OpenSpec", `DumbAware` (validation needs no indexes).

**Decision 2 — Visibility gate on BGT via `isUnderOpenSpec`.**
`getActionUpdateThread() = BGT`; `update()` reads `CommonDataKeys.VIRTUAL_FILE_ARRAY`, shows the item if any selected file `isUnderOpenSpec(file, project)` (short-circuit), else hides via `setEnabledAndVisible(false)`. Do **not** call `super.update()`. The happy path of `isUnderOpenSpec`/`getOpenSpecRoot` is cheap (cached VFS lookups + path compare); the only heavy branch (`refreshAndFindFileByPath`) can't fire here because a tree-selected file's `openspec/` ancestor is already in VFS. No read action needed.

**Decision 3 — File → target resolution + scoped validate.**
A pure resolver maps the clicked path to a `ValidateTarget`: `SPEC(<cap>)` for `openspec/specs/<cap>/**`, `CHANGE(<name>)` for active `openspec/changes/<name>/**`, `WHOLE_PROJECT` for `openspec/changes/archive/**`, the `openspec/` root, or `config.yaml`. The `--type` is known from the branch, so the CLI call is `validate <id> --type change|spec --json` (no ambiguous-name path). Built-in scoped validate uses `BuiltInValidator.validateChange(name)` / spec-level methods.

**Decision 4 — Factor the validate body into a shared `protected` method.**
Extract `OpenSpecValidateAction`'s `Task.Backgroundable` body into `protected void runValidation(Project, ValidateTarget)` (target `WHOLE_PROJECT` = today's behavior). Both the toolbar action (always `WHOLE_PROJECT`) and the Project-View action (resolved target) call it — one built-in+CLI merge implementation, scoped by target.

**Decision 5 — Contract-test the single-item CLI shape.**
The single-item `validate <id> --json` envelope (`{items:[{id,type,valid,issues,durationMs}], summary, version, root}`) differs from the bulk shape the plugin already parses. Capture a real single-item fixture and contract-test the parser rather than hand-authoring it.

## Risks / Trade-offs

- **`update()` cost** at menu-build frequency → the path check is cheap and short-circuits; the heavy VFS-refresh branch is unreachable for tree selections.
- **Archived-change sharp edge** — `validate <archived-name> --type change` returns a misleading "No deltas found" error → resolver classifies `archive/**` as `WHOLE_PROJECT`, never forwarding an archived name.
- **Multi-select spanning items** → validate the whole project (or the first resolved item); MVP validates whole-project when the selection isn't a single clean item — simplest and safe.
- **Multi-IDE menu** → `anchor="last"`, text self-describing ("Validate OpenSpec"), no relative anchor.

## Migration Plan

Additive. Steps: (1) extract `runValidation(project, target)` in `OpenSpecValidateAction` (behavior-preserving for the whole-project path); (2) add the resolver (`ValidateTarget` + `resolveTarget(files, project)`); (3) new `OpenSpecValidateFromProjectViewAction` with the gated `update()`; (4) register in `plugin.xml` under `ProjectViewPopupMenu`; (5) capture the single-item fixture + contract test; (6) `verifyPlugin`. Rollback is a straight revert.

## Open Questions

- **Multi-select policy** — validate whole-project when the selection spans multiple items or isn't a single clean change/spec, vs. validating each. Recommend whole-project fallback for MVP; finalize in implementation.
- **CLI vs built-in for the scoped path when both available** — mirror the existing action (built-in always + CLI merge), scoped; confirm the console reporting labels the target ("Change `<name>`" / "Spec `<cap>`") rather than a file name.
