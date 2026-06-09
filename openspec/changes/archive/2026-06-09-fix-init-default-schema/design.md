## Context

`ScaffoldingService` handles the "Init OpenSpec in this project" action. Two paths: CLI delegation (`initWithCli`) when the OpenSpec CLI is available, built-in fallback (`initBuiltIn`) when not. The built-in path writes `openspec/config.yaml` directly from `TemplateProvider.configYamlTemplate(schema, version)`.

At `ScaffoldingService.java:140` the schema argument is the string literal `"spec-driven"`. The version argument resolves through a helper (`getVersionSupport()` → `OpenSpecSettings.getEffectiveVersion(project)` → `VersionSupport.fromString`) — that pattern already handles the "user setting overrides default" case for version. The schema field has no analogous helper; the setting is read at the settings panel level but never threaded down to the scaffolding path.

This was latent because, until `openspec-1-4-baseline` shipped, the Default schema combo had only one option (`spec-driven`), so the divergent path was unreachable. 1.4.x added `workspace-planning`, exposing the gap.

## Goals / Non-Goals

**Goals:**
- The built-in init path honors `OpenSpecSettings.getDefaultSchema()` when it's set.
- The empty-setting case continues to write `spec-driven` (no behavior change for users who haven't customized).
- The fix sits behind a helper symmetric with the existing `getEffectiveVersion` pattern, so future fields read consistently.

**Non-Goals:**
- Changing the CLI delegation path. The CLI owns config generation in that flow; the new 1.4.x `--profile` flag is the correct pass-through there, but `--profile` is a CLI *workflow profile* (`core` / `custom`), not a *workflow schema* (`spec-driven` / `workspace-planning`). Different concepts; out of scope for this bug.
- Migrating existing `openspec/config.yaml` files. The bug affects new init only; projects already initialized with a wrong schema are an unrelated repair path.
- Adding UI affordances for schema selection during init (e.g., a confirm dialog). The setting is the source of truth; trust it.

## Decisions

**Add `getEffectiveSchema(Project)` to `OpenSpecSettings`, mirroring `getEffectiveVersion(Project)`.** Two reasons. (1) Symmetry: the version field has the helper pattern; readers of the call site at line 140 should see the same shape for both arguments. (2) Encapsulation: the fallback default (`"spec-driven"`) belongs near the field, not at every caller. Alternative considered: inline `getDefaultSchema()` with a fallback at the single ScaffoldingService call site. Rejected because (a) it duplicates the fallback string if any future caller needs the effective schema and (b) the existing `getEffectiveVersion` pattern already proved this is the project's preferred shape.

**Fallback to the literal `"spec-driven"`, not to `VersionSupport.<latest>.getValidSchemas().iterator().next()` or similar.** The fallback is the historical default — what the code wrote when the setting was empty. Computing it from `VersionSupport` would be more "principled" but risks shifting the default whenever upstream adds a schema (since `validSchemas` is a Set with unspecified iteration order). The literal is intentional and stable.

**Add a single test in `ScaffoldingContractTest`, not a parameterized matrix.** The behavior under test is "the setting is read." One positive case (`workspace-planning` set → rendered template contains `schema: workspace-planning`) plus the existing implicit `spec-driven`-default coverage in surrounding tests is sufficient. A matrix over all valid schemas would be over-engineering; the codepath is identical for any string the helper returns.

**No spec change to the "Configuration parsing" requirement.** That requirement covers reading existing `openspec/config.yaml` files. This fix is about *writing* config.yaml at init time — a different responsibility, covered by the "Project detection and initialization" requirement's "Init falls back to built-in" scenario. The delta extends that scenario set; it doesn't touch parsing.

## Risks / Trade-offs

**A test relying on `OpenSpecSettings` may need a mock or fixture.** Existing `ScaffoldingContractTest` already exercises `TemplateProvider` directly without project services; the new test needs the settings layer. → Mitigation: use the same test-scaffolding pattern as `OpenSpecSettingsPanelProfileTest` (or similar in the test suite) which mocks `OpenSpecSettings.getInstance(project)`. If no clean mock pattern exists, fall back to calling `TemplateProvider` directly with the schema argument the production code would have produced — covers the contract without requiring the project context. The choice is implementation detail; the spec scenario is unambiguous either way.

**A user with stale Default schema (e.g., one set during the `tdd`/`rapid` UI bug era) could trigger writing an invalid schema.** → Mitigation: this fix doesn't introduce that risk — those values were never writable from the current settings panel post-`tdd`/`rapid` removal #176. If a stale state value exists in `openspec.xml`, `getDefaultSchema()` returns it and we write it as-is; the validator will then flag the resulting config.yaml. That's the right behavior (surface the bad state to the user) and is unchanged by this fix.

**Edge case: setting Default schema during an active session, then triggering Init, races against the settings panel's persistence.** → Mitigation: `PersistentStateComponent` persists synchronously on Apply; by the time Init runs, the state is committed. No new race here.

## Migration Plan

None. Behavior change is strictly additive (the setting now functions), and the default-empty case is unchanged. No migration script, no flag, no schema rewrite. Rollback is a single-commit revert.
