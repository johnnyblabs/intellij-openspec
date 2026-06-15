## 1. D1 — Drop profile gating from `OpenSpecSyncAction`
- [x] 1.1 Remove the `getWorkflowId()` override from `OpenSpecSyncAction`
- [x] 1.2 Add `OpenSpecSyncActionTest` verifying enablement regardless of profile

## 2. D2 — Combo lists only CLI-accepted presets
- [x] 2.1 Update `OpenSpecSettingsPanel.WORKFLOW_PROFILE_PRESETS` to `["", "core"]` (drop `"custom"`)
- [x] 2.2 Refactor `OpenSpecSettingsPanelProfileTest.customPreset_rendersWithSelectedWorkflowsHint` into orphan-rendering coverage
- [x] 2.3 ~~Update `corePreset_rendersWithFiveEssentials` assertion~~ → folded into D7 (landed together): renderer no longer enumerates workflow names; test renamed `corePreset_rendersWithGenericHint` and now asserts the absence of `propose/explore/apply/sync/archive` to lock in the principle.

## 3. D3 — "Customize workflows…" button + IntelliJ Terminal launcher
- [ ] 3.1 Add "Customize workflows…" button to `OpenSpecSettingsPanel` (secondary/link style)
- [ ] 3.2 Wire button to open IntelliJ Terminal tool window + run `openspec config profile`
- [ ] 3.3 Render non-modal "I'm done" banner; on click → synchronous `WorkflowProfileService.refresh()`
- [ ] 3.4 Add fallback refresh triggers: project open, Settings reset/apply, status bar popup, tool window focus
- [ ] 3.5 Implement terminal-unavailable fallback (clipboard copy + notification with docs link)
- [ ] 3.6 Trigger existing two-step prompt for `openspec update` after refresh

## 4. D4 — Static discovery cue, no `EXPANDED_WORKFLOWS`
- [x] 4.1 Remove `EXPANDED_WORKFLOWS` constant and `AVAILABLE_IN_CUSTOM_HEADER` from `OpenSpecProfileStatusBarWidget`
- [x] 4.2 Replace popup reveal with a static cue (using "Run `openspec config profile` in a terminal" until D3 ships the button-based wording the design specifies)
- [x] 4.3 Relabel active item: `● custom (your workflow set) · N workflows (active)`

## 5. D5 — Commit `Workflow-Profiles.md` to `main`
- [ ] 5.1 Update `scripts/docs/wiki/Workflow-Profiles.md` content to reflect D2 (no `custom` combo entry, Customize affordance noted)
- [ ] 5.2 `git add` + commit the wiki page to `main`

## 6. D6 — Orphan profile recovery UX
- [x] 6.1 Render inline help text near combo when selected value is orphan
- [x] 6.2 Disable Apply button while orphan is selected
- [x] 6.3 Update `OpenSpecConfigurable.isModified` / `apply` to honor the orphan gate

## 7. D7 — ContextHelpLabel copy rewrite (no workflow enumeration)
- [x] 7.1 Update `OpenSpecSettingsPanel` profile-help copy to drop workflow names (rewritten to general framing + CLI-direct guidance; references to a "Customize workflows…" button deferred until D3 ships)
- [x] 7.2 Add docs link affordance in the ContextHelpLabel (already present via `ContextHelpLabel.createWithLink` → `OpenSpecProfileStatusBarWidget.DOCS_URL` — page lives on GitHub `main`)
- [x] 7.3 Update test assertion referencing the old enumerated copy (handled under 2.3 — same renderer text covered both)

## 8. Spec sync
- [ ] 8.1 Update `openspec/changes/profile-ui-cli-alignment/specs/` delta specs to reflect implemented decisions
- [ ] 8.2 Run `openspec validate --change profile-ui-cli-alignment`

## 9. Verification
- [ ] 9.1 `./gradlew test` — all tests pass
- [ ] 9.2 Sandbox: Sync Specs action enabled in core profile (D1 regression closed)
- [ ] 9.3 Sandbox: workflow profile combo lists only "(default)" and "core"; orphan shows recovery help; Apply disabled while orphan selected
- [ ] 9.4 Sandbox: "Customize workflows…" opens IntelliJ Terminal with `openspec config profile`; "I'm done" refreshes inline
- [ ] 9.5 Sandbox: status bar widget popup shows static discovery line, no workflow enumeration
- [ ] 9.6 Sandbox: ContextHelpLabel reads generic copy + docs link
