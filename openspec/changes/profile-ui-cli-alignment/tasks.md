## 1. D1 — Drop profile gating from `OpenSpecSyncAction`
- [x] 1.1 Remove the `getWorkflowId()` override from `OpenSpecSyncAction`
- [x] 1.2 Add `OpenSpecSyncActionTest` verifying enablement regardless of profile

## 2. D2 — Combo lists only CLI-accepted presets
- [x] 2.1 Update `OpenSpecSettingsPanel.WORKFLOW_PROFILE_PRESETS` to `["", "core"]` (drop `"custom"`)
- [x] 2.2 Refactor `OpenSpecSettingsPanelProfileTest.customPreset_rendersWithSelectedWorkflowsHint` into orphan-rendering coverage
- [x] 2.3 ~~Update `corePreset_rendersWithFiveEssentials` assertion~~ → folded into D7 (landed together): renderer no longer enumerates workflow names; test renamed `corePreset_rendersWithGenericHint` and now asserts the absence of `propose/explore/apply/sync/archive` to lock in the principle.

## 3. D3 — "Customize workflows…" button + IntelliJ Terminal launcher
- [x] 3.1 Add "Customize workflows…" button to `OpenSpecSettingsPanel` (right of combo on the same row; default `JButton` — link-styling deferred since IDE-conventional placement next to the affordance reads as clearly secondary).
- [x] 3.2 New `OpenSpecTerminalLauncher` helper wraps `TerminalToolWindowManager.createShellWidget(...)` (post-deprecation API) with try/catch on `Throwable` so a stripped IDE missing the terminal plugin falls back gracefully. `bundledPlugin("org.jetbrains.plugins.terminal")` added to `build.gradle.kts` for compile-time access. Panel uses an injectable `TerminalLauncher` functional interface so tests can stub the outcome.
- [x] 3.3 Non-modal banner in the right column below the combo with "I'm done" + "Cancel" buttons. "I'm done" runs `WorkflowProfileService.refresh()` on a pooled thread (not synchronous on EDT — design's "synchronous" was a UX statement, not threading; blocking EDT on a CLI call would be wrong), then on EDT hides the banner, calls `panel.refreshConfigProfileSection()`, surfaces a "Now on `X · N workflows`" toast.
- [x] 3.4 Fallback refresh triggers landed across four surfaces: `OpenSpecProjectService$StartupDetection.execute` (project open), `OpenSpecConfigurable.scheduleProfileRefresh` called from `reset()` + `apply()`, `OpenSpecProfileStatusBarWidget.getPopup` (popup open), `OpenSpecToolWindowFactory` listener (tool window shown). All run refresh on `executeOnPooledThread` with silent `Throwable` swallow.
- [x] 3.5 Terminal-unavailable fallback: clipboard copy via `Toolkit.getSystemClipboard()` + warning notification on `OpenSpec.System` group with an "About profiles…" action linking to `DOCS_URL`. Notification body comes from `OpenSpecTerminalLauncher.fallbackMessage(command)`.
- [x] 3.6 After refresh, gated on `service.hasChangedSinceLastRefresh()`: calls `WorkflowProfileSwitchService.promptAndRunUpdateIfConfirmed(activeProfileName)`. Cache is primed via `service.getActiveWorkflows()` before `refresh()` so the diff flag is meaningful — first-call always-false semantics would otherwise swallow real changes.

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
- [x] 7.1 Update `OpenSpecSettingsPanel` profile-help copy to drop workflow names (rewritten to general framing + CLI-direct guidance; D3 follow-up applied: copy now reads "click \"Customize workflows…\"" instead of the "run `openspec config profile` in a terminal" stopgap, matching the affordance the button delivers)
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
