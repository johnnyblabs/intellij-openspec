## Why

The plugin's built-in (CLI-less) init path hardcodes `schema: spec-driven` regardless of the user's chosen Default schema setting. Pre-existing bug, latent until openspec-1-4-baseline shipped — that change made `workspace-planning` the first legitimately user-selectable alternative, so the divergent code path is now exercisable. A user setting Default schema to `workspace-planning` in Settings → Tools → OpenSpec, then triggering the IDE's Init action, sees `openspec/config.yaml` written as `schema: spec-driven` anyway. The setting is silently ignored.

## What Changes

- Add `getEffectiveSchema(Project)` to `OpenSpecSettings`, mirroring the existing `getEffectiveVersion(Project)` pattern at the same call site. Returns `getDefaultSchema()` when non-empty, falls back to `"spec-driven"` otherwise.
- Update `ScaffoldingService.initBuiltIn` to thread `OpenSpecSettings.getInstance(project).getEffectiveSchema(project)` through to `TemplateProvider.configYamlTemplate` instead of the hardcoded `"spec-driven"` string at `ScaffoldingService.java:140`.
- Leave the CLI delegation path (`initWithCli`, around line 120) unchanged. The CLI owns config.yaml generation in that path; the new 1.4.x `--profile` flag is the appropriate pass-through if/when needed (deferred — `workspace-planning` is a *schema*, not a CLI *profile*; they're orthogonal concepts).
- Add a `ScaffoldingContractTest` scenario that sets Default schema to `workspace-planning` and asserts the rendered config.yaml's `schema:` line matches. Existing `spec-driven`-default coverage stays.

No new public API surface for plugin consumers; behavioral change is "the setting now does what it says."

## Capabilities

### New Capabilities
<!-- None — bug fix on existing behaviour. -->

### Modified Capabilities
- `plugin-core`: the "Project detection and initialization" requirement gains a scenario covering "default schema honored on built-in init". The MODIFIED requirement text says the same thing; only the scenario set expands.

## Impact

- **Code:** `src/main/java/com/johnnyblabs/openspec/scaffolding/ScaffoldingService.java` (one-line change at line 140 + helper), `src/main/java/com/johnnyblabs/openspec/settings/OpenSpecSettings.java` (one new method).
- **Tests:** `src/test/java/com/johnnyblabs/openspec/scaffolding/ScaffoldingContractTest.java` — one new test asserting the rendered template's schema field matches the configured default.
- **Specs:** `openspec/specs/plugin-core/spec.md` "Project detection and initialization" requirement — one new scenario.
- **Plugin behavior:** users who never set a Default schema see no change (fallback is the same `"spec-driven"` value previously hardcoded). Users who *do* set Default schema get the behavior the setting promises.
- **Risk:** very low. Single source file path with explicit fallback; no migration path needed; existing tests cover the unchanged default behavior.

## References

- Tracker: the linked issue (the original bug report — has full repro steps and fix sketch)
- Tracker: the linked issue
- Surfaced by: `openspec-1-4-baseline` (committed as `109a6be`, archived 2026-06-06) which made `workspace-planning` user-selectable

No new Forgejo/Plane trackers created for this change — the existing bug report has the canonical body. When this change archives, the archive flow closes the linked tracker issue.
