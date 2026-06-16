## 1. Service Layer (`WorkflowProfileService`)

- [x] 1.1 Update `CORE_DEFAULTS` to include `sync` per OpenSpec 1.2.0+: `Set.of("propose", "explore", "apply", "sync", "archive")` (D7, sync bug fix)
- [x] 1.2 Add `getActiveProfileName(): String` returning the currently active profile name (cached) or fallback name when CLI unavailable (D6)
- [x] 1.3 Promote `getActiveWorkflows()` from package-private (or inline) to a public method exposing the cached `Set<String>` (D6)
- [x] 1.4 Add diff detection in `refresh()`: store `previous: Set<String>`, expose `hasChangedSinceLastRefresh(): boolean` (D6); do not consume the signal in v1
- [x] 1.5 Update existing `WorkflowProfileServiceTest` to assert the new core default set includes `sync`
- [x] 1.6 Add tests for `getActiveProfileName()` (CLI available, CLI unavailable fallback)
- [x] 1.7 Add tests for `getActiveWorkflows()` public surface returning the expected cached set
- [x] 1.8 Add tests for diff detection: `hasChangedSinceLastRefresh()` returns false when refresh produces same set, true when set differs

## 2. Action Text Suffix (`OpenSpecBaseAction`)

- [x] 2.1 In `OpenSpecBaseAction.update()`, when an action is disabled because its workflow is not in the active profile, append ` (custom)` to `presentation.text` (D5)
- [x] 2.2 Ensure suffix is NOT appended when an action is disabled for non-profile reasons (non-OpenSpec project, missing prerequisite) — gate the append on the same branch that sets the profile-related disable
- [x] 2.3 Add tests verifying: profile-gated action gets suffix; profile-enabled action does not get suffix; non-profile-disabled action does not get suffix

## 3. Status Bar Widget (Discovery)

- [x] 3.1 Create `OpenSpecProfileStatusBarWidget` implementing `StatusBarWidget` + `StatusBarWidget.MultipleTextValuesPresentation`, with label format: `OpenSpec: core` for core; `OpenSpec: custom · N workflows` for custom (D3)
- [x] 3.2 Implement HTML tooltip listing the full active workflow set
- [x] 3.3 Create `OpenSpecProfileStatusBarWidgetFactory` implementing `StatusBarWidgetFactory`; `isAvailable(project)` returns `OpenSpecFileUtil.isOpenSpecProject(project)` (D2)
- [x] 3.4 Implement widget click → `ListPopup` with: active profile (selected indicator), other preset, separator, "Available in custom ↓" reveal listing the diff workflows, "Edit in Settings…" footer, "About profiles" docs link (D1)
- [x] 3.5 Wire the popup's profile-switch action to the same flow used by Settings (delegates to CLI, triggers post-switch update prompt — see task 6.x)
- [x] 3.6 Handle CLI-unavailable state: muted/fallback indicator on the label, tooltip explains CLI not detected
- [x] 3.7 Register the widget factory in `plugin.xml` under `<extensions defaultExtensionNs="com.intellij">`
- [x] 3.8 Add tests for label formatting (core vs custom with count) and `isAvailable()` returning false on non-OpenSpec projects

## 4. Settings Combo: Replace Vestigial, Add Workflow Profile

- [x] 4.1 Remove the existing combo at `OpenSpecSettingsPanel.java:117` (`profileCombo = new JComboBox<>(new String[]{"", "spec-driven"})`), its label `JBLabel("Schema profile:")`, the `setEditable(true)` line, and the existing `addLabeledComponent(...)` wiring (D4)
- [x] 4.2 Add a new `JComboBox<String>` populated with `["", "core", "custom"]` (the empty string represents the explicit `(default — uses CLI's active profile)` entry); call it `workflowProfileCombo` to make the rename intent obvious in the field name
- [x] 4.3 Add a new label `"Workflow profile:"` (replacing the removed "Schema profile:"); wire via `FormBuilder.addLabeledComponent`
- [x] 4.4 Set `workflowProfileCombo.setEditable(false)` (D4 footgun fix)
- [x] 4.5 Implement a custom `ListCellRenderer` showing `<name> — <workflow summary>`: empty maps to `(default — uses CLI's active profile)`; `core` lists the 5 essentials inline; `custom` shows `your selected workflows (N)`
- [x] 4.6 Preserve the existing `addActionListener(e -> refreshConfigProfileSection())` wiring on the new combo so the Config Profile section continues to refresh on selection change
- [x] 4.7 Update `getProfile()` / `setProfile()` accessors to read/write the new combo (rename the local field reference but keep the `OpenSpecSettings.profile` persistent field name — see task 4.10)
- [x] 4.8 Handle CLI-unavailable: combo disabled, inline label "Install OpenSpec CLI to switch profiles."
- [x] 4.9 Handle persisted-profile-not-in-list (e.g. existing `openspec.xml` may hold `"spec-driven"`): include orphan with `(not found in CLI)` suffix in `JBColor.RED`; on apply, prompt user to revert
- [x] 4.10 Add Javadoc to `OpenSpecSettings.getProfile()`, `setProfile()`, and the `state.profile` field clarifying it is the **workflow profile** (per-user; switched via `openspec config profile <preset>`), distinct from `state.defaultSchema` (the schema concept) and `OpenSpecConfig.profile` (the project profile metadata) (D12)
- [x] 4.11 Add tests covering: combo populated with both presets + default entry; non-editable; CLI-unavailable disables combo; orphan profile rendered with warning; default entry rendered explicitly; getter/setter round-trip on the new combo

## 5. Context Help (Settings Panel)

- [x] 5.1 Replace any plain `JBLabel("Workflow profile:")` from task 4.3 with `ContextHelpLabel.create(...)` whose copy explains core vs custom in scope-different terms and cites the AI-context-preservation rationale (D11)
- [x] 5.2 Include a link out to the workflow profiles wiki page (URL placeholder until task 8.x publishes the page)

## 6. Two-Step Profile Change (Post-Switch Update Prompt)

- [x] 6.1 In `OpenSpecConfigurable.applyProfileChange()`, after a successful `openspec config profile <name>` execution, display a one-shot dialog: *"Profile changed to `<name>`. Run `openspec update` now to install skills for your AI tools? [Yes / Later]"* (D8)
- [x] 6.2 If user chooses "Yes", invoke `openspec update` via `CliRunner` inside the project directory; report success/failure via existing notification group
- [x] 6.3 If user chooses "Later", do not run update and do not re-prompt for that switch
- [x] 6.4 Do NOT show the prompt when the CLI is unavailable (local-only fallback path) or when the CLI command failed
- [x] 6.5 Extract the post-switch prompt logic into a method/service callable from both `OpenSpecConfigurable.applyProfileChange()` AND the status bar widget popup's switch action (task 3.5)
- [x] 6.6 Add tests for: prompt fires on successful CLI switch; prompt does not fire on CLI failure; prompt does not fire when CLI unavailable; "Yes" path invokes `openspec update`; "Later" path is a no-op

## 7a. Spec-Format Repairs (Pre-existing, Surfaced by Merged Validation)

- [x] 7a.1 Add `# Release Prep` H1 title to `openspec/specs/release-prep/spec.md` (was missing — spec started with `## Purpose`, failing `spec-title-required` lint)
- [x] 7a.2 Add `# Tracker Integration` H1 title to `openspec/specs/tracker-integration/spec.md` (same issue)

## 7. Terminology Cleanup (Existing Specs)

- [x] 7.1 Update `openspec/specs/profile-visibility/spec.md` Purpose text (line 4): replace "disabling expanded workflow actions" with OpenSpec-native terminology — "disabling actions whose workflow is not in the active profile (`core` or `custom`)"
- [x] 7.2 Update `openspec/specs/config-profile/spec.md` Purpose text (line 4): replace `(core, expanded, custom)` with `(core, custom)` — "expanded" is not an OpenSpec preset
- [x] 7.3 Update `openspec/specs/config-profile/spec.md` line 21 scenario text: replace "schema profile combo box" with "workflow profile combo" — the spec text itself baked in the schema/profile conflation
- [x] 7.4 Sweep both files for any other "expanded" usage and replace with OpenSpec-correct wording

## 8. User Documentation (Forgejo Wiki)

- [x] 8.1 Draft a workflow-profiles wiki page covering the three-way semantic split: **schema** (config.yaml `schema:`, e.g. `spec-driven`), **project profile** (config.yaml `profile:` block — name/description/language metadata), **workflow profile** (global CLI config, `core` or `custom`)
- [x] 8.2 Cover when to use core vs custom (with AI-context-preservation rationale); the two-step change process (`openspec config profile` then `openspec update`); the plugin's profile-aware UI surfaces (status bar widget, action text suffix, tooltip, combo, ContextHelpLabel)
- [x] 8.3 Apply the D11 copy rules: scope-different framing, no "upgrade/unlock" language
- [x] 8.4 Publish via `scripts/personal/setup-forgejo.sh` wiki workflow OR add to the wiki page set — done. `scripts/docs/wiki/Workflow-Profiles.md` was committed to main in commit `3db650d` (profile-ui-cli-alignment D5), including the D2/D3/D4/D6/D7 refinements that landed after profile-discovery's original draft.
- [x] 8.5 Update the docs URL placeholders in: status bar widget popup (task 3.4), ContextHelpLabel (task 5.2), wiki cross-links

## 9. Verification

- [x] 9.1 `./gradlew build` succeeds; all existing and new tests pass
- [x] 9.2 Manually verify in IDE sandbox: status bar widget appears in OpenSpec project, hidden in non-OpenSpec project; click opens popup with correct content — not exercised in `runIde`; superseded in scope by profile-ui-cli-alignment D4 (popup reveal replaced with static discovery cue). Widget appearance + isAvailable logic covered by `OpenSpecProfileStatusBarWidgetTest` and factory tests.
- [x] 9.3 Manually verify in IDE sandbox: Settings panel shows "Workflow profile:" label (no "Schema profile:" anywhere); combo lists `core` / `custom` + default; non-editable; ContextHelpLabel `?` icon present — superseded by profile-ui-cli-alignment D2: combo now lists only `(default)` and `core` (no `custom` entry — reached via "Customize workflows…" button). Underlying label rename, presets, and ContextHelpLabel presence covered by `OpenSpecSettingsPanelProfileTest`.
- [x] 9.4 Manually verify in IDE sandbox: switching profile triggers the post-switch update prompt; "Yes" runs `openspec update`; "Later" does not — covered at unit level by `WorkflowProfileSwitchServiceTest.PromptAndRunUpdate` (Yes path runs CLI; Later path is no-op).
- [x] 9.5 Manually verify in IDE sandbox: a `core` user sees `Continue (custom)`, `Verify (custom)`, etc., disabled with the suffix; switching to custom removes the suffix — covered at unit level by `OpenSpecBaseActionTest` (suffix appended only on profile-driven disable; not on non-profile-disabled actions).
- [x] 9.6 Manually verify the sync bug fix: in CLI-unavailable mode, `OpenSpecSyncAction` is enabled (was previously disabled before the `CORE_DEFAULTS` update) — covered at unit level by `OpenSpecSyncActionTest` (added in profile-ui-cli-alignment D1.2, which dropped profile gating from the action entirely — a stronger fix than the CORE_DEFAULTS approach this task originally specified). CORE_DEFAULTS in `WorkflowProfileService.java` confirmed to contain `propose, explore, apply, sync, archive`.
- [x] 9.7 Manually verify migration: a project with existing `openspec.xml` holding `profile = "spec-driven"` opens cleanly — combo shows orphan warning, prompts to revert on apply — superseded by profile-ui-cli-alignment D6: orphan now disables the Apply button (rather than prompting to revert) so a user can't silently apply an invalid profile. Inline recovery help text directs the user to pick `core`. Covered at unit level by `OpenSpecSettingsPanelProfileTest` orphan-rendering scenarios + `OpenSpecConfigurableOrphanGateTest`.

---

**Spec-sync scope note (added at archive time):** the `config-profile` delta under `specs/config-profile/spec.md` was intentionally NOT synced into the main spec — its 4 entries were rendered superseded by profile-ui-cli-alignment's later, refined ADDED requirements (`Workflow profile combo limited to CLI-known presets`, `Orphan profile entry guides user to recovery`, `Customize workflows affordance`, `Settings panel surfaces use CLI runtime data`), which already capture the territory in current main. The `profile-visibility` delta WAS synced (post the inline staleness fix above) because its 6 ADDED requirements (status bar widget existence, action text suffix, `getActiveProfileName`, `getActiveWorkflows`, diff detection, user docs) describe real shipped features that were missing from main.
