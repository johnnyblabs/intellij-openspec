## Why

OpenSpec 1.2.0 introduced a workflow profile system to prevent "skill bloat" in AI context windows: the **core** profile (default) ships only the 5 essential workflows (`propose`, `explore`, `apply`, `sync`, `archive`), while **custom** lets the user opt in to a chosen subset of expanded workflows (`new`, `continue`, `ff`, `verify`, `bulk-archive`, `onboard`). The plugin has partial support for this — it gates actions by workflow ID — but three problems undermine the experience:

1. **The plugin's hardcoded core fallback is wrong.** `WorkflowProfileService.CORE_DEFAULTS` lists 4 workflows (`propose, explore, apply, archive`) and is missing `sync`. A core-profile user with no CLI available today gets the Sync action incorrectly disabled, contradicting OpenSpec 1.2.0+.

2. **There is no UI surface that announces the active workflow profile.** A core-profile user encountering a disabled action (Continue, Verify, etc.) has no in-context explanation beyond a tooltip they may not hover. The Settings panel's profile combo is hardcoded to `["", "spec-driven"]` (so the user can't even pick `core` or `custom`) and is `setEditable(true)` (a footgun letting users invent profile names the CLI rejects).

3. **Switching profile in Settings only does half the job.** OpenSpec requires a two-step profile change: (a) `openspec config profile <name>` to update the workflow set, (b) `openspec update` inside the project to install the corresponding skills/commands for the user's AI tools. The plugin does step (a) and silently skips (b), leaving AI tool configs stale until the user manually runs `update`.

The result: the *default-experience user* — someone on `core` who hits the most friction — has the least information about why they're seeing it, the broken combo to "fix" it, and no signal that switching profiles needs a follow-up step to take full effect.

This change is designed around the **core-profile user as primary persona**: they are the audience hitting the friction, so every surface is tuned to make their situation legible and their next step obvious. Custom users get accurate state and the same step-2 plumbing.

## What Changes

### Bug fix
- **Update `CORE_DEFAULTS`** in `WorkflowProfileService` to include `sync`, matching OpenSpec 1.2.0+ (`propose, explore, apply, sync, archive`).

### New surfaces (gating / discovery — `profile-visibility` capability)
- **Status bar widget** showing the active profile (`OpenSpec: core` or `OpenSpec: custom · 8 workflows`). Click opens a `ListPopup` (Git-branch-widget pattern) listing the two presets, plus an "Available in custom ↓" reveal that lists the workflows the user would gain by switching, plus a footer link to Settings and an "About profiles" docs link. Hidden on non-OpenSpec projects via `StatusBarWidgetFactory.isAvailable()`.
- **Action text suffix** `(custom)` appended in `OpenSpecBaseAction.update()` when an action is disabled because its workflow is not in the active profile — mirroring JetBrains' `(Ultimate)` convention. The explanation lands at the moment of friction, not buried in a tooltip.
- **`WorkflowProfileService` API expansion**: expose `getActiveProfileName()` and a public `getActiveWorkflows()`, and add diff detection in `refresh()` (`Set<String> previous` comparison) so future cache-invalidation work can fire change notifications without re-architecting.

### Settings improvements (`config-profile` capability)
- **Rip out the broken combo** at `OpenSpecSettingsPanel.java:117` (currently labeled `"Schema profile:"` and populated with `["", "spec-driven"]` — schema-flavored values wired to the workflow profile CLI API; the existing combo has no coherent semantics). Replace with a new combo populated from the OpenSpec workflow profile presets (`core`, `custom`) plus an explicit `(default — uses CLI's active profile)` entry. Each entry rendered with profile name + workflow summary (custom shows count). Set `editable=false` to prevent invented names. Handle: CLI unavailable (combo disabled with inline message), persisted profile no longer in CLI list (warning suffix on the orphan), default/empty.
- **Rename the UI label** from `"Schema profile:"` to `"Workflow profile:"` to match OpenSpec's terminology and end the schema/profile conflation in the UI.
- **Field semantics clarification**: `OpenSpecSettings.profile` is and has always been semantically the *workflow profile* — it is the value passed to `openspec config profile <X>`. No field rename is needed (no consumer outside the Settings flow); add a Javadoc to prevent future confusion. The unrelated `OpenSpecSettings.defaultSchema` field already correctly models the schema concept and is consumed by `WorkflowActionPanel` and `ProposeChangeDialog`; it is not touched by this change.
- **Two-step profile change**: after a successful `openspec config profile <name>`, prompt the user with a one-shot dialog: *"Profile changed to `custom`. Run `openspec update` now to install skills for your AI tools? [Yes / Later]"*. The plugin currently runs only step (a); this closes the gap honestly without auto-modifying files in the user's `$HOME` without consent.
- **`ContextHelpLabel`** next to "Workflow profile:" — IntelliJ's idiomatic `?` icon — with copy framing core and custom as scope-different (not better/worse), citing the AI-context-preservation rationale.

### Cross-cutting
- **User documentation** (Forgejo wiki page) covering: the three-way semantic split — **schema** (config.yaml `schema:`, e.g. `spec-driven`), **project profile** (config.yaml `profile:` block — name/description/language metadata), and **workflow profile** (global CLI config, `core` or `custom`). When to use core vs custom; the two-step change process; what the plugin's profile-aware UI surfaces (widget, suffix, tooltip, combo) do and why.
- **Terminology cleanup in existing specs**: `openspec/specs/config-profile/spec.md` line 4 references a non-existent "expanded" preset (`(core, expanded, custom)`); replace with `(core, custom)`. Line 21 references "schema profile combo box" — replace with "workflow profile combo" so the spec text no longer bakes in the conflation. Similar sweep across `openspec/specs/profile-visibility/spec.md` for "expanded" usage.

## Capabilities

### Modified Capabilities
- `profile-visibility`: extended beyond action enablement to include the status bar widget, the `(custom)` text suffix, and the service API expansion. Existing core-defaults set updated to include `sync`. Purpose text updated to remove "expanded".
- `config-profile`: extended with dynamic CLI-sourced combo, edge-case handling, post-switch update prompt, and ContextHelpLabel. Purpose text updated to remove "expanded".

### Deferred (follow-up changes)
- `profile-cache-coherence`: file watcher on `~/.config/openspec/config.json`, balloon/sticky notifications on detected diff, tool window header strip. The diff-detection plumbing in `refresh()` is included here so the follow-up is purely additive.
- `merge-profile-capabilities`: a no-behavior administrative change to merge `profile-visibility` and `config-profile` into a single `workflow-profile` capability, since both serve one OpenSpec concept. Deferred so v1 can ship UX value without compounding architectural cleanup.

## Impact

- **Bug fix**: a class of core-profile users (those without CLI on PATH) gain access to Sync that was incorrectly gated.
- **New UI surface**: `OpenSpecProfileStatusBarWidget` + `StatusBarWidgetFactory` registered in plugin.xml.
- **Service API**: `WorkflowProfileService` gains `getActiveProfileName()`, public `getActiveWorkflows()`, and diff-aware `refresh()`. No breaking changes; existing `isWorkflowEnabled(String)` consumers are unaffected.
- **Settings panel**: the "Schema profile" combo (vestigial / wired wrong) is removed and replaced with a new "Workflow profile" combo populated by `[core, custom]` plus an explicit default entry; `editable=false`. ContextHelpLabel added. New post-switch prompt dialog.
- **Settings field**: `OpenSpecSettings.profile` keeps its name (it has always semantically been the workflow profile; renaming would churn `OpenSpecConfigurable` with no consumer benefit). Added Javadoc clarifies its meaning. Persisted values change shape (`""` or `"spec-driven"` → `""` or `"core"` or `"custom"`); existing settings will silently re-resolve at next refresh.
- **Schema concept untouched**: `OpenSpecSettings.defaultSchema` (used by `WorkflowActionPanel` and `ProposeChangeDialog`) already correctly models the schema concept and is not modified by this change.
- **Base action**: `OpenSpecBaseAction.update()` gains a 1-line suffix when disabling by profile.
- **No breaking changes**: all new surfaces are additive. Existing action enablement behavior is unchanged except for the sync default-set fix; the suffix is cosmetic. The combo replacement does change persisted settings shape — see above.
- **Persona-driven design**: copy and visual hierarchy are tuned for the core-profile user. Custom users see correct, non-misleading state but are not the optimization target.

## References

- Builds on archived change `2026-03-28-profile-aware-action-visibility` (which introduced `WorkflowProfileService` and the action enablement gate).
- Existing specs: `openspec/specs/profile-visibility/spec.md`, `openspec/specs/config-profile/spec.md`.
- OpenSpec 1.2.0+ profile system documentation (core vs custom presets, `openspec config profile`, two-step change process).
- IntelliJ Platform conventions referenced: `StatusBarWidgetFactory`, `MultipleTextValuesPresentation`, `ContextHelpLabel`, `ListPopup` (mirroring Git branch widget UX).
