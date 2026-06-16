# Design — profile-discovery

This is an outline of the key design decisions. Detail will firm up during implementation; the intent here is to capture the *why* behind each non-obvious choice so future contributors (and future-us) can audit them.

## Persona

**Primary: the core-profile user.** They are the default and the audience hitting the friction (disabled actions with no in-context explanation). Every UI decision here is graded against "does this help that user understand their situation and choose their next step?"

**Secondary: custom-profile users.** They get accurate, non-misleading state. The widget reflects what's active; the combo correctly reflects their CLI; the action suffix never appears for workflows in their selected set. They are not the optimization target, but the post-switch update prompt benefits them too.

## Key context: OpenSpec has *three* related concepts that get conflated

This change deliberately uses the term **workflow profile** (never just "profile") because OpenSpec config has separate, unrelated concepts that are easy to confuse:

| Concept | Lives in | Holds | Purpose |
|---|---|---|---|
| **Schema** | `openspec/config.yaml` `schema:` field | `"spec-driven"` (currently the only schema; experimental CLI surface) | Workflow methodology — defines artifact set (proposal/specs/design/tasks) |
| **Project profile** | `openspec/config.yaml` `profile:` block | name, description, language, framework, vendor | Per-project metadata for AI agents |
| **Workflow profile** | `~/.config/openspec/config.json` `profile` field (or `openspec config get profile`) | `"core"` or `"custom"` (string preset, plus a separate `workflows` array) | Per-user workflow preset selector |

This change concerns the **workflow profile** only. The schema is already correctly modeled in `OpenSpecSettings.defaultSchema` (consumed by `WorkflowActionPanel` and `ProposeChangeDialog`) and is not touched. The project profile is read by `ConfigService` for tree display only and is also out of scope. The user docs deliverable names all three so users aren't confused.

The existing `config-profile` spec text mixes these concepts (e.g., line 21 references a "schema profile combo box"); part of v1's terminology cleanup is to remove that conflation from the spec text itself.

## Decisions

### D1 · Status bar widget click → ListPopup, not Settings

The Git branch widget pattern: click the widget, get a `ListPopup` of switchable values. Forcing users into Settings for a one-line change is wrong friction. The popup includes:

- Current profile (selected indicator)
- Other available preset (`core` ↔ `custom`)
- Separator
- "Available in custom ↓" reveal listing the workflows the user would gain by switching (core-persona affordance)
- "Edit in Settings…" footer
- "About profiles (?)" docs link

Settings remains the *authoritative* surface for full profile detail and the post-switch update prompt; the popup is the *fast* surface for switching. Both call into the same underlying flow, so the post-switch update prompt fires regardless of which surface initiated the switch.

### D2 · Hide widget on non-OpenSpec projects

Same `OpenSpecFileUtil.isOpenSpecProject(project)` check that `OpenSpecBaseAction` already uses. Convention follows the Git widget hiding outside VCS roots. Implemented via `StatusBarWidgetFactory.isAvailable(project)` so the IDE never instantiates the widget when irrelevant.

### D3 · Widget label format depends on profile

```
  active profile     widget label                     why
  ──────────────     ──────────────────────────       ─────────────────────────
  core               OpenSpec: core                    no count — fixed at 5
  custom (8 wfs)     OpenSpec: custom · 8 workflows    count is the actual signal
```

`core` is fixed; the count is implied. `custom` is parameterized — the count is the meaningful information (an 8-workflow custom and a 6-workflow custom are genuinely different). Full breakdown (workflow list) goes in the HTML tooltip and the popup. This mirrors Python's `Python 3.11` (mode in label, version detail in tooltip).

### D4 · Combo: replace, don't fix; rich `ListCellRenderer`, `editable=false`, two known presets

The existing combo at `OpenSpecSettingsPanel.java:117` is labeled `"Schema profile:"` and populated with `["", "spec-driven"]` — schema-flavored values fed into the workflow profile CLI API (`openspec config profile <X>`). It has no coherent semantics today: the values are wrong for the API call, and the label conflates schema with workflow profile.

This is not a "fix the values" change; it's a **replacement**. Rip out the combo, drop the schema-flavored label, add a new combo with proper workflow profile presets.

The current `setEditable(true)` is a footgun — users can type any string, the CLI then rejects it. Hard rule on the new combo: only profiles the CLI knows about (`core`, `custom`) are selectable. The renderer shows:

```
  core    — propose, explore, apply, sync, archive (5 essentials)
* custom  — your selected workflows (8)
```

Source: hardcoded `["core", "custom"]` is acceptable here since the CLI itself only exposes these two presets (custom workflow membership is configured per-user, not via profile name). If OpenSpec ever adds more presets, swap in `openspec config list --json` enumeration.

The persisted field (`OpenSpecSettings.profile`) stays — see D12.

Edge cases:
- **CLI unavailable** — combo disabled, inline label "Install OpenSpec CLI to switch profiles." Preserves the existing local-fallback persistence path only for the empty default; users cannot invent profile names.
- **Persisted profile not in CLI list** — prepend the orphan with a `(not found in CLI)` suffix in red; on apply, prompt to revert.
- **Default/empty** — render as a literal `(default — uses CLI's active profile)` entry mapped to `""`; never expose empty-string as raw.

### D5 · `(custom)` text suffix in `OpenSpecBaseAction.update()`

JetBrains uses `(Ultimate)` as a text-level signal for IDE-edition-gated features. Borrow the convention: when an action is disabled because its workflow is not in the active profile, append ` (custom)` to the action text. This places the explanation *at* the moment of friction — visible without hover, in menus where tooltips are easy to miss.

Note the suffix is `(custom)` not `(expanded)` — "expanded" was never an OpenSpec preset name. The suffix telegraphs *which preset* unlocks the action. Disappears the moment the user switches profiles.

### D6 · `WorkflowProfileService` API expansion

Two new public surfaces and one internal change:

- `getActiveProfileName(): String` — needed by widget label.
- `getActiveWorkflows(): Set<String>` — promote from private to public; needed by widget popup ("Available in custom ↓" reveal needs to compute the diff between core and the user's profile).
- Diff detection in `refresh()` — keep `Set<String> previous` and compare; expose `hasChanged: Boolean` or fire a project message-bus event. Not consumed in v1 (the widget refreshes off the same trigger), but unblocks the follow-up `profile-cache-coherence` change without re-architecting.

### D7 · Bug fix: CORE_DEFAULTS includes `sync`

OpenSpec 1.2.0+ defines core as 5 workflows: `propose, explore, apply, sync, archive`. The plugin's hardcoded fallback lists 4 (missing `sync`), causing `OpenSpecSyncAction` to be incorrectly disabled when the CLI is unavailable. Update `WorkflowProfileService.CORE_DEFAULTS` to the correct set. Tiny code change; lives with this work because the suffix logic depends on the correct defaults (otherwise core users see `(custom)` next to Sync, which is wrong).

### D8 · Two-step profile change — option B (prompt, don't auto-run)

`openspec config profile <name>` only updates the workflow set. To install the corresponding skills/command files for AI tools (Cursor, Claude Code, Copilot, etc.) the user must also run `openspec update` inside the project. The plugin currently does step (a) and silently skips (b).

Three options were considered:

- **A** — auto-run `openspec update` after profile change. Lowest friction, but silently modifies files in `$HOME/.claude/`, `.cursor/`, etc. without consent.
- **B** — prompt: *"Profile changed. Run openspec update now to install skills for your AI tools? [Yes / Later]"*. Honest, gives the user agency. **Selected.**
- **C** — surface as banner / next-step in Settings. Least intrusive but easy to ignore; leaves the gap unsolved.

The prompt fires once after a successful profile switch (any surface — Settings combo, status bar popup). "Later" is a real option, not a suggestion to never do it.

### D9 · Notifications deferred to follow-up

The principle "notify only on *external* change, never on user-initiated change" is articulated here so the follow-up implements it correctly, but no notification code lands in v1. The diff-detection plumbing (D6) is included so the follow-up is purely additive.

### D10 · Tool window header deferred to follow-up

The status bar widget is "always-on, every project"; the tool window header would be "contextual, while doing OpenSpec work." Both are valuable; v1 ships the always-on surface first because it has higher leverage (visible even when the tool window is collapsed). Header lands with the cache-coherence work since both touch the tool window panel.

### D11 · Docs framing — scope-different, with explicit AI-context rationale

A risk in the core-first persona framing: the copy could imply custom is *better*. It isn't — they're scoped differently. OpenSpec's stated rationale for core's minimalism is **AI context window preservation** ("skill bloat"). Lean into that. Hard rules for all user-facing copy in this change:

- ✅ "Core ships only the essential workflows to keep AI context windows lean."
- ✅ "Need verify, sync customization, or onboard? Switch to custom and pick what you want."
- ✅ "Core covers the propose→archive loop. Custom lets you opt in to expanded workflows."
- ❌ "Unlock more workflows" / "Upgrade to custom" / "Get the full experience"

Custom workflows get a brief but neutral mention; they are opt-in by design.

### D12 · `OpenSpecSettings.profile` keeps its name

Audit found that `state.profile` in `OpenSpecSettings`:
- Is consumed only by `OpenSpecSettingsPanel.refreshConfigProfileSection()` and `OpenSpecConfigurable.applyProfileChange()`
- Is passed verbatim to `openspec config profile <X> [--json]`
- Has no other callers in the codebase

Semantically, the field IS the workflow profile — it just got the wrong name in the UI label and the wrong values in the combo, not a wrong field name. Renaming the field (e.g. to `workflowProfile`) would churn `OpenSpecConfigurable` and the persistent state schema (`openspec.xml`) for zero consumer benefit.

Resolution:
- Keep the field name `profile`.
- Add a Javadoc on `OpenSpecSettings.getProfile()` / `setProfile()` / the `state.profile` field clarifying it is the **workflow profile** (per-user, switched via `openspec config profile <preset>`), not the schema (`OpenSpecSettings.defaultSchema`) and not the project profile (`OpenSpecConfig.profile` map).
- Rename only the UI label (`"Schema profile:"` → `"Workflow profile:"`).

Persisted-value migration is implicit: existing `openspec.xml` may hold `""` or `"spec-driven"`. Both are treated as "default" by the new combo (the orphan-handling case in D4 catches anything else); no explicit migration code is needed.

### D13 · Schema concept is out of scope

`OpenSpecSettings.defaultSchema` already correctly models the schema. It has live consumers (`WorkflowActionPanel.java:300`, `ProposeChangeDialog.java:58`, `SetupWizardModel`) and no Settings UI exposure today. We deliberately do not add a UI for it in this change — schema is experimental (`openspec schema` is marked so), only `spec-driven` exists, and adding a control would expand scope without user-visible value.

If a future schema becomes non-experimental, the proper home for that UI is a separate change, modeled the same way as the new workflow profile combo (label = "Schema:", values from `openspec schemas --json`).

### D14 · Spec capability split — distribute, don't merge (yet)

The new requirements split cleanly across the existing two capabilities by topic:

```
  profile-visibility       gating + discovery + service infra
                           (status bar widget, action suffix,
                            getActiveProfileName, diff detection,
                            updated CORE_DEFAULTS)
                           
  config-profile           Settings panel UI (dynamic combo,
                           ContextHelpLabel, post-switch update prompt)
```

Both existing capabilities serve one OpenSpec concept (workflow profile) and could conceptually merge into a single `workflow-profile` capability. We deliberately defer that merge to a separate follow-up admin change so v1 ships UX value without compounding architectural cleanup. Mixing "rename + merge two capabilities" with "ship new UI + fix a bug + add a step-2 prompt" makes review harder and increases blast radius.

## Open questions (to settle during implementation)

- Status bar widget icon: reuse `/icons/openspec.svg` (already shipped) or a profile-specific glyph?
- Where exactly should the "About profiles" docs link point — Forgejo wiki URL, or a future site?
- Should the post-switch update prompt remember "Later" for that session, or re-fire on any subsequent action that would benefit from updated skills?

## Non-decisions (intentionally not addressed)

- Multi-profile-per-area / monorepo profile splits — not an OpenSpec concept today; out of scope.
- Plugin-side custom-profile *workflow editing* — CLI owns the lifecycle; plugin is read-mostly for the workflow set.
- Workflow chip profile-awareness in `WorkflowActionPanel` — the 2026-03-28 design explicitly deferred this; revisit only if user data shows it bites.
- Project profile (`config.yaml profile:` block) display — out of scope here. If we surface it later, it lives in a separate capability.
