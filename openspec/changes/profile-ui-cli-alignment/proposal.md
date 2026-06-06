## Why

The just-released `profile-discovery` change (v0.2.10) baked design assumptions about the OpenSpec CLI's profile surface that diverge from what CLI 1.3.1 actually exposes: `custom` is not a switchable preset, `sync` is not in core workflows, and the plugin's hardcoded "expanded workflows" set is already stale. Users on a current CLI hit *"Unknown profile preset 'custom'"* when applying the combo and see `Sync Specs (custom)` disabled in core mode — both are regressions from the design's assumed CLI behavior.

The fix is structural, not cosmetic: the plugin's profile UI must be driven off the CLI's runtime output (`openspec config list --json`), not off design-time assumptions or detected CLI version. The CLI publishes the truth on every invocation; any second source of truth (version-keyed presets, hardcoded workflow lists) drifts.

## What Changes

- Remove profile gating from `OpenSpecSyncAction` — sync is a view/diff utility (like Validate, List, Refresh) and was never appropriate to gate. Eliminates the `Sync Specs (custom)` disabled regression on real CLI regardless of whether the CLI's core profile includes `sync`.
- Workflow profile combo lists only real CLI-accepted presets (today: `["", "core"]`). The released `custom` third option is removed because `openspec config profile custom` is rejected by CLI 1.3.1 ("Available presets: core").
- Add a "Customize workflows…" button next to the combo that launches the OpenSpec CLI's interactive picker (`openspec config profile`) in IntelliJ's Terminal tool window, with a non-modal "I'm done" completion handshake that triggers a synchronous `WorkflowProfileService.refresh()`. Folded into `config-profile` (no new capability).
- Replace the hardcoded `EXPANDED_WORKFLOWS = {new, continue, ff, verify, bulk-archive, onboard}` set in `OpenSpecProfileStatusBarWidget`. The popup keeps a static, name-free discovery cue ("Run Customize workflows… to see what's available") that links to docs — no plugin-side workflow enumeration.
- Improve orphan-profile recovery UX: when a legacy `profile = "custom"` is loaded, the Settings panel renders inline help text explaining how to recover, and Apply is disabled while an orphan is selected so the no-op behavior isn't silent.
- Rewrite the ContextHelpLabel copy to drop the hardcoded workflow names ("propose, explore, apply, sync, archive" / "verify, ff, continue, bulk-archive, onboard, new") that already rot against CLI 1.3.1 and link to the canonical docs page instead.
- Fix the "About profiles…" 404 — commit `scripts/docs/wiki/Workflow-Profiles.md` to GitHub `main` so the existing `DOCS_URL` resolves.
- Establish the architectural principle in `config-profile` spec: the plugin's profile UI is driven off CLI runtime data, not detected CLI version. Version detection is reserved for *feature-availability gating* (cf. existing `SchemaService.isSchemaSupported()` 1.2.0+ check), not for hardcoding what a preset means.

## Capabilities

### New Capabilities

None. The "Customize workflows…" affordance is folded into `config-profile` rather than promoted to its own capability — it's a single button + completion handshake, not a distinct surface with independent behavior worth specifying separately.

### Modified Capabilities

- `config-profile`: combo preset list narrows to CLI-known presets; `custom` is no longer offered as a switch target. "Customize workflows…" affordance launches the CLI picker in IntelliJ Terminal with an "I'm done" completion handshake. Orphan recovery UX adds inline help and Apply-disabled state. ContextHelpLabel copy drops hardcoded workflow names. Architectural principle "Settings panel surfaces use CLI runtime data" is captured as a normative requirement.
- `profile-visibility`: `Workflow ID mapping` updated so Sync Specs returns `null` (joins the utility list). `Profile-aware action enablement` updated to make explicit that view/diff utilities are out of gating scope regardless of name collision.
- `plugin-documentation`: `Workflow-Profiles.md` is committed to GitHub `main` at `scripts/docs/wiki/Workflow-Profiles.md`, the page describes the Settings combo accurately (no `custom` switch entry), and the canonical workflow profiles documentation is the single point at which workflow lists may be enumerated.

## Impact

**Code**:
- `src/main/java/com/johnnyblabs/openspec/actions/OpenSpecSyncAction.java` — remove `getWorkflowId()` override.
- `src/main/java/com/johnnyblabs/openspec/settings/OpenSpecSettingsPanel.java` — drop `custom` from `WORKFLOW_PROFILE_PRESETS`; add "Customize workflows…" button + completion banner with "I'm done" action; orphan inline help text; Apply-disabled state when orphan selected; ContextHelpLabel copy rewrite (no workflow enumeration).
- `src/main/java/com/johnnyblabs/openspec/settings/OpenSpecConfigurable.java` — apply gate when orphan selected; refresh hook on Settings reset/apply.
- `src/main/java/com/johnnyblabs/openspec/statusbar/OpenSpecProfileStatusBarWidget.java` — remove `EXPANDED_WORKFLOWS`; replace popup reveal with a static "Run Customize workflows… to see what's available" line; relabel active item from `● custom · N workflows (active)` to `● custom (your workflow set) · N workflows (active)` to defuse the naming asymmetry against the (now `custom`-less) Settings combo.
- `scripts/docs/wiki/Workflow-Profiles.md` — commit to GitHub `main`; update content to match D2 (no `custom` combo entry).

**Tests**:
- Update `OpenSpecSettingsPanelProfileTest`: combo no longer contains `"custom"`; orphan rendering still covers the legacy persisted case; Apply-disabled state when orphan selected.
- New tests for the customize-button → terminal-launch flow (mockable via a stubbed Terminal tool window) and the "I'm done" → `WorkflowProfileService.refresh()` handshake.
- Update `OpenSpecProfileStatusBarWidgetTest`: popup no longer hardcodes `EXPANDED_WORKFLOWS` reveal items; new active-label format assertion.
- Update sync action gating test (currently asserts `(custom)` suffix in core fallback — invert to assert never-gated regardless of profile).

**Migration / data**:
- Users who somehow persisted `profile = "custom"` under the v0.2.10 release will see it orphan-render with the existing red `(not found in CLI)` suffix. No new migration code needed — the `setProfile()` orphan path from `profile-discovery` already handles arbitrary unknown values.
- No `OpenSpecSettings` field changes; persisted state surface is stable.

**External**:
- No CLI version requirement change. Plugin's existing CLI compatibility window (1.2.0+) is unaffected.
- No breaking changes to public actions, services, or extension points.

## References

- Forgejo: johnb/intellij-openspec#203
- Plane: openspec/issue/216 (`0ecc4b76-ab0e-41b8-bb91-95dba315f854`)
- Predecessor change: `profile-discovery` (released v0.2.10)