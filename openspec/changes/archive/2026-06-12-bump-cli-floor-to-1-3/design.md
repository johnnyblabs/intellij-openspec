## Context

`VersionSupport` (in `version/`) is the plugin's model of `openspec/config.yaml`'s `version:` field — the **config-format version**. Three enum values today: `V1_0` (proposal only), `V1_1` (proposal + design), `V1_2` (proposal + design + specs + tasks).

The **CLI version** is a separate axis modeled as a plain `String detectedVersion` field in `CliDetectionService`. `SchemaService` has private comparison logic and a `MIN_CLI_VERSION = "1.2.0"` constant gating schema-management features.

`OpenSpecProjectService.StartupDetection` is an existing `ProjectActivity` that runs at project open, performs CLI detection, and shows `OpenSpecNotifier.cliMissing(project)` if no CLI is found. It's the natural insertion point for a floor check that fires when CLI is present but too old.

## Goals / Non-Goals

**Goals:**
- The plugin no longer maintains code paths for the V1_0 and V1_1 config-format baselines. Anyone with a `version: 1.0.0` or `1.1.0` config still works (routed to V1_2 baseline), but the plugin doesn't carry separate validator branches for them.
- Schema management requires CLI 1.3+ instead of 1.2+.
- Users on a CLI version below 1.3.0 see a one-time, useful notification when opening an OpenSpec project — not a silent feature degradation.
- The two existing call sites that compare CLI versions (SchemaService, new floor check) share one helper instead of duplicating logic.

**Non-Goals:**
- Adding a `V1_3` or `V1_4` enum entry. The config-format shape hasn't changed (probed during openspec-1-4-baseline); `version: 1.3.x` and `version: 1.4.x` continue to route to V1_2 baseline correctly.
- Removing the no-CLI fallback paths. Those serve users without any CLI installation — orthogonal to the CLI version floor.
- Wiring `init --profile` into the init wizard. Tracked as a separate concern (mentioned in #207 reframe; will get its own ticket).
- Building a "Plugin requires CLI 1.4+ to function" hard gate. We want a soft floor (notification + degradation), not a hard cutoff. Users on pre-1.3 should continue to function for everything the plugin can do without the CLI.
- Migrating any existing `openspec/config.yaml` files. The version field stays whatever the user has; if it's `1.0.0`, the plugin treats it as V1_2 baseline going forward (slight expansion of required artifacts, but the validator already warns rather than errors on artifact-mismatch).

## Decisions

**Keep the `V1_2` enum value name as `V1_2`.** It refers to the config-format version baseline, NOT the CLI version. Renaming to `V1_3` would create false symmetry between two orthogonal axes and break the prefix-match logic in `fromString` (which uses the first 3 characters of the version string). The config-format version is still 1.2.0 as of CLI 1.4.x per the openspec-1-4-baseline probe.

**Soft floor (notification + degradation), not hard cutoff.** Decided against making the plugin refuse to operate when CLI < 1.3. Two reasons. (1) Pre-1.3 users on a working plugin install would be surprised by a hard error — soft notification respects their existing investment. (2) The plugin's no-CLI fallback paths already handle CLI-absence gracefully; routing pre-1.3 users through those paths is a small additional behavior matter, not a new degradation surface. Alternative considered: hard refusal (disable tool window). Rejected because it makes the "stay on plugin v0.2.10 if you want" advice in the CHANGELOG bullet less honest — if we can soft-degrade, we should.

**Extract a `CliVersion.atLeast` static utility.** Both call sites need the same comparison logic. Extracting now avoids near-term duplication; the API is tiny (`public static boolean atLeast(String detected, String required)`). Alternative considered: leave SchemaService's private comparison alone and duplicate in the new floor check. Rejected because it's two call sites and one signature — the cost of extracting is negligible and the cost of duplicating compounds.

**Notification fires from `StartupDetection.execute`, not from a tool-window listener.** The startup activity is the existing hook that runs once per project open. Tool-window activation can fire multiple times per session (open/close/reopen the tool window). Per-project-open is the correct cadence for "your CLI is old" — repeating it on every tool-window open would be noise.

**Notification copy is action-oriented, not apologetic.** "Upgrade: `npm i -g @fission-ai/openspec@latest`" — single command. No long explanation. The notification has the standard "Don't show again" affordance from `OpenSpecNotifier`'s existing infrastructure; users who actively prefer their old CLI can dismiss it permanently.

**Don't add a `V1_3` enum entry as part of this change.** Config-format version hasn't changed upstream. If a future CLI version introduces a new config-format requirement, *that* change adds the enum entry. Adding V1_3 now would be speculative and would either duplicate V1_2's content (sourceless boilerplate) or invent new required-artifact constraints that upstream hasn't declared.

## Risks / Trade-offs

**A V1_0 / V1_1 reference in code I missed throws NPE at runtime.** → Mitigation: grep for `V1_0` and `V1_1` across `src/` before committing. Test suite includes a full-resolution test (`fromString` for "1.0.0", "1.1.0", null, empty, garbage); any unhandled enum reference will surface there or via compile failure (deleted enum value is a compile error at any direct reference).

**A user's `openspec/config.yaml` declares `version: 1.0.0` and they hit validator differences after the floor bump.** → The change routes their config to V1_2 baseline, which has a stricter required-artifact set (proposal + design + specs + tasks vs V1_0's proposal-only). The existing validator already warns (not errors) on artifact-mismatch, so the user sees additional warnings but their workflow doesn't break. Mitigation: this is a documented and welcome outcome — the plugin is now nudging them toward the modern baseline. Not actually a risk.

**SchemaService floor bump breaks users who rely on schema-management features on CLI 1.2.x.** → They lose access to fork/init/list inside the IDE. Mitigation: they can still use the CLI directly. The notification points to the upgrade command. If they upgrade, schema management returns. Trade-off accepted — the goal is to make 1.3 the floor, not preserve 1.2-special-case behavior.

**Notification fatigue.** Users who can't or won't upgrade their CLI might find the per-project-open notification annoying. → `OpenSpecNotifier`'s "Don't show again" affordance handles this. Worst case: one notification per project, dismissible. Acceptable.

**Forward-compat: a future config-format `version: 1.3.0` shows up before we add V1_3.** → It routes to V1_2 via prefix-match (`"1.3.0".startsWith("1.2.0".substring(0,3))` is `false` actually — `"1.3.0".startsWith("1.2")` is `false`). Wait — re-check. Looking at the current `fromString`:

```java
if (v.version.equals(version) || version.startsWith(v.version.substring(0, 3))) {
```

`v.version.substring(0, 3)` of `"1.2.0"` is `"1.2"`. So `"1.3.0".startsWith("1.2")` is `false` — V1_2 doesn't match `1.3.0` via prefix. It falls through to `return V1_2` (the default-to-latest fallback). Same outcome by a different path. **Trade-off accepted:** acceptable today (since no V1_3 config-format exists upstream); becomes a problem only if upstream adds V1_3 — at which point we add V1_3 enum entry. No action needed here.

## Migration Plan

None. Existing `openspec/config.yaml` files keep working; their `version:` field is interpreted as V1_2 baseline regardless of what's in it (pre-1.3 versions). New plugin installs see no behavioral difference unless they have CLI 1.0/1.1/1.2 (notification + degradation as above).

Rollback: a single-commit revert. The deleted enum values and the new floor check are isolated to a handful of files; restoring them brings the prior behavior back atomically.
