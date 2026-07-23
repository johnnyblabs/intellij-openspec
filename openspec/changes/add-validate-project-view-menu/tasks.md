## 1. Target resolution (pure, testable)

- [ ] 1.1 Add a `ValidateTarget` (kind = SPEC/CHANGE/WHOLE_PROJECT + id) and a pure `static resolveTarget(VirtualFile[] files, Project project)`: `openspec/specs/<cap>/**` → `SPEC(<cap>)`; active `openspec/changes/<name>/**` → `CHANGE(<name>)`; `openspec/changes/archive/**`, `openspec/` root, or `config.yaml` → `WHOLE_PROJECT`; a selection spanning multiple distinct items or no clean item → `WHOLE_PROJECT`. Never forward an archived change name with `--type change`.

## 2. Share the validate pipeline

- [ ] 2.1 Factor `OpenSpecValidateAction`'s `Task.Backgroundable` body into `protected void runValidation(Project, ValidateTarget)` — `WHOLE_PROJECT` preserves today's behavior exactly (`validateAll()` + CLI `validate --all --json`); `SPEC(id)`/`CHANGE(id)` run the built-in single-target method (`validateChange(id)` / spec-level) always, and the CLI `validate <id> --type change|spec --json` when available, merged. The existing action's `actionPerformed` calls `runValidation(project, WHOLE_PROJECT)`.
- [ ] 2.2 Console reporting labels the resolved target ("Change `<name>`" / "Spec `<cap>`" / whole project), not a file name. No per-file valid/invalid verdict.

## 3. Project View action + registration

- [ ] 3.1 New `OpenSpecValidateFromProjectViewAction extends OpenSpecValidateAction`: `getActionUpdateThread()` = `ActionUpdateThread.BGT`; `update()` reads `CommonDataKeys.VIRTUAL_FILE_ARRAY`, `setEnabledAndVisible(true)` iff any file `OpenSpecFileUtil.isUnderOpenSpec(file, project)` (short-circuit) — do NOT call `super.update()`. `actionPerformed` resolves the target via `resolveTarget(...)` and calls `runValidation(project, target)`. Mark `DumbAware`.
- [ ] 3.2 `plugin.xml`: register the action (text "Validate OpenSpec", description, icon) with `<add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>` — NOT a relative anchor.

## 4. Fixture + tests

- [ ] 4.1 Capture a real single-item `openspec validate <id> --type change|spec --json` fixture (both a change and a spec case) into `src/test/resources/fixtures/cli/1.6.0/` (isolated XDG, sanitize `root.path`); the single-item envelope (`{items:[{id,type,valid,issues,durationMs}], summary, version, root}`) differs from bulk. Add a `CliContractTest` case parsing it into the validation-result model — no hand-authored shapes.
- [ ] 4.2 Unit-test `resolveTarget` (pure): spec path → SPEC(cap); active change path → CHANGE(name); archive path → WHOLE_PROJECT; `config.yaml`/root → WHOLE_PROJECT; multi-item selection → WHOLE_PROJECT. Both directions (a valid spec resolves to SPEC; a non-item resolves to WHOLE_PROJECT).
- [ ] 4.3 Unit-test the action `update()` visibility with `TestActionEvent` + a temp/`LightVirtualFile` selection: under `openspec/` → visible; outside → hidden; empty selection → hidden.
- [ ] 4.4 (If practical headlessly) test that `runValidation(project, target)` routes to the scoped built-in validate for SPEC/CHANGE vs `validateAll()` for WHOLE_PROJECT.

## 5. Docs + build

- [ ] 5.1 Update `docs/feature-reference.md` (and the wiki) to note Validate is available from the Project View context menu, scoped to the clicked change/spec. Vendor-neutral.
- [ ] 5.2 Update `CHANGELOG.md` `## Unreleased` (user-facing, vendor-neutral: right-click an openspec file in the Project view to validate its change or spec). No tracker identifiers.
- [ ] 5.3 Run `./gradlew build` green (suite + coverage floor; ratchet WITH MARGIN if coverage rose, per the 2026-07-23 floor note). Run **`./gradlew verifyPlugin`** — new action registration + `com.intellij.*` data-key references must resolve on 2024.2 (the pre-push hook fires it automatically).
