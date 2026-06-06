## Context

The `profile-discovery` change (released v0.2.10) baked three CLI assumptions that don't hold against OpenSpec CLI 1.3.1:

1. **`custom` is a switchable preset.** It isn't. `openspec config profile [preset]` accepts only named presets the CLI knows; today that's `core`. Selecting "custom" in the combo and clicking Apply triggers `openspec config profile custom`, which the CLI rejects: *"Unknown profile preset 'custom'. Available presets: core"*.
2. **`sync` is in core workflows.** It isn't on 1.3.1: `openspec config list --json` reports `["propose", "explore", "apply", "archive"]`. The `OpenSpecSyncAction.getWorkflowId() = "sync"` therefore makes Sync Specs render `(custom)`-suffixed and disabled in core.
3. **The plugin can locally enumerate "expanded workflows"** (the hardcoded `EXPANDED_WORKFLOWS = {new, continue, ff, verify, bulk-archive, onboard}` set in `OpenSpecProfileStatusBarWidget.java:46`). It can't reliably — there's no CLI command that publishes the full available workflow set. `openspec --help`, `openspec config --help`, and `openspec config profile --help` all confirm: the only structured workflow data path is `openspec config list --json`, which reports only the *active* set.

The CLI surface (verified on 1.3.1):
- `openspec config profile [preset]` — switches to a named preset, or opens an interactive TTY picker when invoked with no args.
- `openspec config list --json` — reports active `profile` (string) and `workflows` (array).
- `openspec config set workflows '[…]'` — directly writes an arbitrary workflow array (per `config set` semantics; not yet exercised by the plugin).
- No `--json` for `config profile`, no standalone `config workflows` subcommand, no all-workflows enumeration.

Constraint that follows: the plugin cannot drive an in-plugin multi-select dialog without either (a) hardcoding the universe of workflow strings (the rot we're trying to fix) or (b) mining the CLI's interactive picker (brittle). Any "Customize workflows…" UX must either delegate to the CLI's interactive picker or accept the hardcoded-rot trade-off.

## Goals / Non-Goals

**Goals:**
- Eliminate the two user-facing regressions on CLI 1.3.1: failing `custom` preset switch, disabled Sync Specs in core.
- Drive the profile UI off CLI runtime data only. No version-keyed preset assumptions, no second source of truth for workflow membership.
- Provide a path to a non-preset workflow set without re-introducing hardcoded workflow lists.
- Repair the broken `DOCS_URL`.

**Non-Goals:**
- Redesigning what "custom" means semantically. The status bar widget's "OpenSpec: custom · N workflows" *display* label is correct (CLI returns `profile: "custom"` when workflows diverge from any named preset) and stays.
- Adding CLI version detection. The principle is *runtime data > version string*; this change actively removes the version-coupling that the hardcoded `EXPANDED_WORKFLOWS` set implied.
- Building a rich workflow management UI inside the plugin. The CLI owns workflow management; the plugin reflects it.
- Backfilling tests for the v0.2.10 release. New tests cover the new behavior; obsolete assertions get updated.

## Decisions

### D1: Drop profile gating from `OpenSpecSyncAction`

**Decision:** Remove the `getWorkflowId()` override from `OpenSpecSyncAction`. Sync joins the unguarded utility actions (Validate, List, Refresh, Update, Init).

**Rationale:** Sync is a view/diff utility — it merges delta specs into main specs with preview. It doesn't introduce workflow concepts the user opted out of by picking core. Gating it created a regression that's actually a category error: `getWorkflowId()` is for actions whose *concept* belongs to a profile-conditional workflow, not for every action whose *name* coincides with a CLI workflow string. The fix is structural, not a CLI-version workaround.

**Alternatives considered:**
- *Special-case sync in CORE_DEFAULTS.* Already done in profile-discovery and exactly what created the regression on real CLI. Rejected.
- *Detect CLI version and gate conditionally.* Reintroduces version coupling. Rejected per the architectural principle.

### D2: Combo lists only CLI-accepted presets

**Decision:** `WORKFLOW_PROFILE_PRESETS` becomes `["", "core"]` (default + core). When the CLI publishes additional named presets in future versions, the plugin will need a way to discover them — see Open Questions. For now, the combo is honest about what it can switch to.

**Rationale:** The combo's contract is "selecting an entry switches to that preset." Having an entry that always fails on Apply is broken. `custom` belongs in the *display* surface (status bar widget label, settings panel "Active profile" readout), not the *switch* surface.

**Alternatives considered:**
- *Keep `custom` and intercept the apply to open the customize affordance instead.* Confusing — "custom" then means two different things in two different places. Rejected.
- *Keep `custom` and make Apply call `openspec config set workflows '[hardcoded full set]'`.* Reintroduces the hardcoded workflow list. Rejected.

### D3: "Customize workflows…" launches the CLI picker in IntelliJ's Terminal with an explicit "I'm done" handshake

**Decision:** Add a "Customize workflows…" button (styled as a secondary/link button, distinct from primary action buttons like Apply) next to the workflow profile combo. Clicking it:
1. Opens (or focuses) the IntelliJ Terminal tool window and runs `openspec config profile` (no args → interactive TTY picker).
2. The Settings dialog stays open with a non-modal banner: *"Waiting for the workflow picker — click **I'm done** when finished."* The banner has an explicit **I'm done** button.
3. When the user clicks **I'm done**, the plugin calls `WorkflowProfileService.refresh()` synchronously, updates the combo and Config Profile section inline, and surfaces a confirmation toast: *"Now on `custom · 7 workflows`"* (or whatever the new state is).
4. After confirmed refresh, the existing two-step prompt offers `openspec update` (same `WorkflowProfileSwitchService.promptAndRunUpdateIfConfirmed` flow as preset switch).

A fallback refresh path also runs `WorkflowProfileService.refresh()` automatically on the following triggers, in case the user dismisses the banner without clicking I'm done: project open, Settings dialog reset/apply, status bar widget popup open, OpenSpec tool window focus gained. The lag is bounded to "next interaction" — never "next IDE restart."

**Rationale:** Honest delegation — the CLI knows the workflow universe, the plugin doesn't. The terminal-launcher pattern preserves zero plugin-side workflow knowledge while staying inside the IDE (Terminal tool window ships bundled). The "I'm done" handshake fixes the cohesion problem of dropping a Settings-dialog user into a TTY without explicit completion feedback — without it, the user closes Settings thinking they're done, sees no UI change, and assumes broken.

**Alternatives considered:**
- *In-plugin multi-select dialog.* Requires the plugin to enumerate available workflows. Either hardcoded (rot) or extracted from the CLI's picker output (brittle). Rejected.
- *External terminal (osascript/Windows Terminal).* Loses IDE integration; cross-platform launchers add complexity. Rejected.
- *Implicit refresh-on-next-interaction without an "I'm done" button.* The original design. Rejected after UX review — leaves the user guessing whether anything took effect.
- *No customize affordance, only docs link.* Acceptable but underdelivers on the user's "I'm on core, I want one extra workflow" use case. Held as fallback if D3's terminal integration proves fragile in tasks-phase exploration.

**Terminal-tool-window-unavailable fallback:** if the Terminal tool window is unavailable (plugin disabled or unsupported IDE variant), the plugin shows a notification copying `openspec config profile` to the clipboard with explicit guidance: *"Open a terminal in your project directory and paste — the command has been copied. See [About profiles] for details,"* with action buttons linking to the docs.

### D4: Replace the hardcoded `EXPANDED_WORKFLOWS` reveal with a name-free static discovery cue

**Decision:** Remove `EXPANDED_WORKFLOWS` and the `AVAILABLE_IN_CUSTOM_HEADER` (which currently enumerates `new, continue, ff, verify, bulk-archive, onboard`). Replace with a single static popup line: *"Run Customize workflows… to see what's available"* — no specific workflow names. The popup retains active/inactive preset entries, the new static discovery line, "Customize workflows…", "Edit in Settings…", and "About profiles…".

The active item label also changes from `● custom · N workflows  (active)` to `● custom (your workflow set) · N workflows  (active)` to defuse the asymmetry against the (now `custom`-less) Settings combo. Without this clarification, a user who just saw the Settings combo lists only `core` will read the popup's "active = custom" label as a contradiction.

**Rationale:** The original reveal hardcoded a workflow list that rots — already wrong on 1.3.1. There is no CLI command that publishes the full workflow set, so any specific list is rot. But dropping discovery entirely (the prior design) sacrifices a UX surface that helps users find the customize path. A name-free static line preserves the discovery cue ("there's more than core; click here") without re-introducing plugin-side workflow enumeration. Specific workflow names live exclusively in the docs page (D5), updated alongside CLI changes.

**Alternatives considered:**
- *Compute the diff from a `config get workflows.*` style probe.* No such command exists.
- *Hardcode the list against the latest CLI version we've tested.* Rot, version-coupling. Rejected.
- *Drop the popup discovery cue entirely.* The prior design. Rejected after UX review — D2 + dropping the reveal together gut discoverability of "what is custom and why would I want it."
- *Source workflow names from a docs string in plugin resources.* Single source still, but enumeration in plugin code surfaces. Rejected — keeps the rot surface in code where the principle says it shouldn't be.

### D5: Commit `Workflow-Profiles.md` to `main` (don't repoint URL)

**Decision:** `git add scripts/docs/wiki/Workflow-Profiles.md` and push to `main`. The existing `OpenSpecProfileStatusBarWidget.DOCS_URL` (which points at `https://github.com/johnnyblabs/intellij-openspec/blob/main/scripts/docs/wiki/Workflow-Profiles.md`) then resolves correctly with no code change.

**Rationale:** Simplest fix. The file is already drafted (7.4KB, locally), the URL was chosen to point at this exact path, and committing it requires zero code or infrastructure change. Forgejo wiki hosting is a separate maintenance surface that's already used for other docs (per existing wiki contents) but introduces a host-mismatch with the GitHub-hosted plugin marketplace listing.

**Alternatives considered:**
- *Move to Forgejo wiki and repoint URL.* Wiki hosting is more conventional for plugin docs but creates two doc surfaces (GitHub for this repo's `scripts/docs/wiki/` markdown files, Forgejo for the rendered wiki). Defer that consolidation to a separate docs-housekeeping change.

**Page content updates required:** the existing draft of `Workflow-Profiles.md` includes a section enumerating the three Settings-combo entries (default, core, custom). After D2, that section is wrong — update the page in the same commit to reflect the two-entry combo and the Customize workflows… affordance.

### D6: Orphan profile recovery UX (inline help + Apply-disabled)

**Decision:** When the workflow profile combo's selected entry is an orphan (not in `WORKFLOW_PROFILE_PRESETS`, e.g., a legacy `"custom"` from v0.2.10), the Settings panel SHALL render an inline help line near the combo: *"This entry is from a previous plugin version. Pick `core` to revert, or click Customize workflows… to define a workflow set."* The Apply button SHALL be disabled while an orphan is the selected combo value.

**Rationale:** The existing orphan rendering (red text + `(not found in CLI)` suffix) is alarming but uninstructive — the user sees something is wrong but has no guidance for recovery. Compounding this, Apply with the orphan selected is a no-op (per D2's combo contract), so the user clicks Apply, nothing happens visibly, and the red label stays. Adding inline help text turns the orphan state into a recovery affordance; disabling Apply removes the silent no-op trap.

**Alternatives considered:**
- *Auto-revert orphan to `core` on combo load.* Rejected — silently overwrites user-persisted state, loses their context, and obscures the migration story.
- *Show a one-shot dialog explaining the orphan state on first Settings open.* Rejected — heavy for a recovery action that the inline approach handles in-flow.

### D7: ContextHelpLabel copy rewrite (no workflow enumeration)

**Decision:** Rewrite the workflow profile ContextHelpLabel copy to drop the hardcoded workflow names. Current copy enumerates *"propose, explore, apply, sync, archive"* (already wrong on 1.3.1 which has 4 workflows in core, not 5) and *"verify, ff, continue, bulk-archive, onboard, new"* (the same hardcoded EXPANDED_WORKFLOWS set D4 removes). New copy: general framing only, with a link to the canonical docs page.

**Suggested new copy:**
> *Workflow profiles control which OpenSpec commands are installed for your AI tools. Core ships a small essential set to keep AI context windows lean. To use additional workflows, click "Customize workflows…" — the OpenSpec CLI will show you what's available. Switching profiles is a two-step process: change profile, then run `openspec update`.*
>
> **Read the full guide →** (links to `Workflow-Profiles.md`)

**Rationale:** ContextHelpLabel is plugin-source-controlled copy. Enumerating workflow names there reintroduces the same rot surface D4 is removing — and it's already inaccurate on 1.3.1. Generic framing + a docs link keeps the copy correct against any CLI version while preserving the educational role of the help affordance.

## Risks / Trade-offs

- **[Risk] D3's "I'm done" handshake assumes the user clicks the banner button.** If they dismiss the banner or close Settings without clicking, refresh falls back to next-interaction triggers. → *Mitigation:* the fallback trigger list (project open, Settings reset/apply, status bar widget popup, OpenSpec tool window focus) is deliberately broad so the lag window is small in practice.
- **[Risk] D3 assumes IntelliJ's Terminal tool window is available.** The Terminal plugin is bundled in IDEA Community and Ultimate but theoretically can be disabled. → *Mitigation:* fallback notification with `openspec config profile` copied to clipboard plus explicit "open a terminal and paste" guidance + docs link.
- **[Risk] D2 means a user with persisted `profile = "custom"` from v0.2.10 will see it as orphan.** → *Mitigation:* D6 adds inline recovery help text and disables Apply while orphan is selected, so the recovery path is in-flow rather than the user staring at a red error with no next action.
- **[Risk] D4's static discovery line is still hardcoded text — it can't reference workflow names without re-rotting, but a reader might infer specifics from "what's available" wording.** → *Mitigation:* keep the cue maximally generic ("Run Customize workflows… to see what's available"). Specific workflow names live only in the docs page (D5), which is updated alongside CLI changes.
- **[Risk] D7's generic copy is less informative than the original enumerated copy was, for users who can't or won't follow the docs link.** → *Mitigation:* the inline cue + Customize button + status bar popup + docs link form a four-surface discovery path. Any single surface being light is acceptable.
- **[Trade-off] D1 (sync ungating) means a user who explicitly removed `sync` from their custom workflow set still sees Sync Specs enabled in the menu.** This is acceptable — sync is a view/diff utility, not a workflow-conditional command. Treating workflow membership as the authority for menu visibility was the original category error.

## Migration Plan

**Deploy:**
1. Land code changes (D1–D4, D6, D7) and the committed wiki file (D5) in a single PR.
2. Tag a patch release (v0.2.11 candidate, milestone v0.3 once cycle prep is done).
3. The CHANGELOG entry calls out the two user-visible fixes (sync now enabled in core, custom-preset apply no longer fails).

**Rollback:**
- v0.2.10 remains on Marketplace as a fallback. The plugin's persisted state (`OpenSpecSettings.profile`) format is unchanged, so downgrading is safe.
- If D3's terminal integration proves too fragile in real-world testing, fall back to "no Customize affordance" + docs link (the alternative from D3). This is a UI-only retreat; specs and other decisions don't depend on D3's specific implementation.

**Forward compatibility:**
- When OpenSpec CLI publishes additional named presets in future, the combo population needs a discovery path (currently `WORKFLOW_PROFILE_PRESETS` is a constant). See Open Questions.

## Open Questions

1. **How should the plugin discover additional CLI presets when they appear?** Today only `core` is published; the hardcoded `["", "core"]` is honest for now but couples the plugin to a single preset name. Possibilities: (a) attempt the switch and catch the rejection, treating the error message's "Available presets:" list as a discovery surface (parsing CLI errors is brittle); (b) wait for an upstream CLI command like `openspec config presets --json`; (c) document that adding a new preset requires a plugin update. Defer to a follow-up change once a second preset actually exists.
2. **Should "Customize workflows…" appear on the status bar widget popup as well as the Settings panel?** The popup has "Edit in Settings…" which routes to the same place. Symmetry says yes; minimalism says one entry point is enough. Resolve in tasks phase based on UI density.

## Resolved Questions

- **Is `workflow-customization` a distinct enough capability to warrant its own spec?** **Resolved: No.** Folded into `config-profile` modifications. The customize flow is one button + completion handshake — not enough surface area to justify a separate capability. Will revisit if the flow accumulates behavior beyond "launch a terminal command and refresh."