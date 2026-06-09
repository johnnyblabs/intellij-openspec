## 1. OpenSpecSettings — getEffectiveSchema helper

- [x] 1.1 Add `public String getEffectiveSchema(Project project)` to `OpenSpecSettings.java`, immediately after the existing `getEffectiveVersion(Project)` method. Implementation: return `getDefaultSchema()` when non-null and non-empty; fall back to the literal `"spec-driven"` otherwise.
- [x] 1.2 Add Javadoc on the new method explaining: (a) when the fallback applies, (b) that the literal `"spec-driven"` is deliberate (not computed from `VersionSupport`), and (c) cross-reference `getEffectiveVersion(Project)` as the sibling pattern.

## 2. ScaffoldingService — use the helper at line 140

- [x] 2.1 In `ScaffoldingService.java`, change the `TemplateProvider.configYamlTemplate("spec-driven", version.getVersion())` call at line 140 to read the effective schema from settings: `TemplateProvider.configYamlTemplate(OpenSpecSettings.getInstance(project).getEffectiveSchema(project), version.getVersion())`.
- [x] 2.2 Verify no other call sites in `ScaffoldingService` hardcode `"spec-driven"` for config writes. — only the line-140 hit remains; no other hardcoded uses in the file.
- [x] 2.3 Leave `initWithCli` (around line 120) unchanged. The CLI owns config.yaml generation in that path; threading `--profile` is a separate concern (1.4.x CLI flag) and a different abstraction (workflow profile, not schema).

## 3. Test coverage

- [x] 3.1 In `ScaffoldingContractTest`, add a test method `initBuiltIn_honorsDefaultSchemaSetting`. Set `OpenSpecSettings.defaultSchema = "workspace-planning"`, then render `TemplateProvider.configYamlTemplate(settings.getEffectiveSchema(project), "1.2.0")` (or call the helper that production uses) and assert the resulting YAML's `schema:` line contains `workspace-planning`.
- [x] 3.2 Add a sibling test `initBuiltIn_fallsBackToSpecDrivenWhenDefaultSchemaUnset`. Leave `defaultSchema` empty, run the same render path, assert the YAML's `schema:` line contains `spec-driven`.
- [x] 3.3 If the existing test scaffolding for `ScaffoldingContractTest` doesn't already mock or fixture `OpenSpecSettings`, follow the pattern used in `OpenSpecSettingsPanelProfileTest` (or whichever existing test stubs settings for assertions). If no such pattern exists, test against the helper layer rather than the full IntelliJ service stack — the spec scenario is about behavior, not integration. — used `@ExtendWith(MockitoExtension.class)` + `@Mock Project project`, instantiated `OpenSpecSettings` directly (it has a default constructor).
- [x] 3.4 Verify existing `ScaffoldingContractTest` cases still pass without modification (they assert the default-empty path, which is unchanged behaviorally). — will be confirmed by `./gradlew test` in §4.
- [x] 3.5 (bonus) Added defensive `initBuiltIn_fallsBackToSpecDrivenWhenDefaultSchemaIsNull` test guarding against a future regression where `state.defaultSchema` could be null (State class initializes to empty string, but the helper handles null too).

## 4. Verification

- [x] 4.1 Run `./gradlew test` — all existing tests pass, new tests pass. BUILD SUCCESSFUL in 26s.
- [x] 4.2 Run `openspec validate fix-init-default-schema --strict` — change validates cleanly.
- [ ] 4.3 Manual spot-check (deferred to release-prep if needed): in IDE sandbox, set Default schema to `workspace-planning`, trigger Init in a non-OpenSpec project, inspect generated `openspec/config.yaml` — should read `schema: workspace-planning`. — deferred to manual IDE testing before tagging v0.3.0; unit + integration tests cover the contract.

## 5. Spec sync at archive time

- [ ] 5.1 During `/opsx:archive`, sync the delta spec (the new two scenarios in `Project detection and initialization`) into `openspec/specs/plugin-core/spec.md`.
- [ ] 5.2 Close Forgejo #208 (the original bug report) with an archival comment pointing at this change's commit.
- [ ] 5.3 Move Plane OS-219 to Done.
