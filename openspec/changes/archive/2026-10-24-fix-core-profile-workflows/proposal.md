# Proposal

## Why

The plugin shows the wrong workflows for the `core` profile, and gates actions on them. Tracked by the linked tracker entry.

- **The core set is out of date.** The plugin's built-in core set is `propose, explore, apply, sync, archive`. Upstream's core set has changed with the CLI version:
  - **4** workflows (`propose, explore, apply, archive`) before 1.4;
  - **5** (adds `sync`) in 1.4 and 1.5;
  - **6** (adds `update`) from 1.6.

  Checked in each published package's `dist/core/profiles.js`. A fresh config with no profile set has no `workflows` key, so on today's CLI the plugin shows 5 where the CLI installs 6.
- **A stored list is trusted for core.** `openspec config list --json` reports the `workflows` list as stored in the config. Upstream ignores that list unless the profile is `custom`: `getProfileWorkflows` returns the core set for every other profile. A config with `profile: core` and a leftover 4-item list (real on the maintainer's machine) makes the plugin show "core · 4 workflows" and disable `update`, while `openspec init` installs all six. The human `config list` output shows the right six; only `--json` shows the stored list.
- **A custom rule is missing.** From 1.10, upstream adds `sync` to a custom list that has `archive` or `bulk-archive` but not `sync`, because archiving needs it. The plugin doesn't.

As a result, the status bar and Settings show the wrong count, and any action gated on a workflow can be wrongly disabled. That includes the planned Revise action, which needs `update`.

## What Changes

- **Resolve workflows the way upstream does,** given the profile, the stored list and the detected CLI version:
  - **`custom`:** the stored list. From CLI 1.10, `sync` is inserted before the first `archive` or `bulk-archive` when it's missing.
  - **Any other profile, or none:** the core set for the detected CLI version: 4 below 1.4, 5 on 1.4–1.5, 6 from 1.6. A stored list is ignored.
- **Without a CLI,** the fallback is the current core set of 6, not the old 5.
- **No new UI.** The status bar widget and the Settings Config Profile section show the resolved set through the existing shared resolution.
- **Not in this change:** tracker entries that build on `isWorkflowEnabled("update")`, such as the Revise action.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `profile-visibility`: "Workflow profile resolution" mirrors upstream's `getProfileWorkflows`. Core ignores a stored list and uses the CLI version's core set, custom applies the `sync` rule from 1.10, and the no-CLI fallback is the 6-workflow set.

## Impact

- **Code:** `WorkflowProfileService`, which replaces `CORE_DEFAULTS` and `parseSnapshot`'s use of the raw list, and reads the detected CLI version from `CliDetectionService`.
- **Fixtures:** captured from the real 1.15.0 CLI under an isolated `XDG_CONFIG_HOME`:
  - `config list --json` for a fresh config, which has no `workflows`;
  - a stale core config (`profile: core` with a 4-item list);
  - a custom config whose list has `archive` but not `sync`.
- **Tests:** `WorkflowProfileServiceTest` and `OpenSpecSettingsPanelProfileTest`. The tests that treated "`update` is in the set" as proof the raw list was parsed become proof of the core rule instead. New contract tests on the captured shapes must each fail against today's code. Version-boundary tests cover 1.3.x, 1.4.0, 1.5.x, 1.6.0, 1.9.x and 1.10.0.
- **Docs:** `docs/workflow-profiles.md` (or wherever the core set is listed) and the CHANGELOG `## Unreleased` Fixed entry.
- **Platform:** no new platform API. The plugin still supports IntelliJ IDEA 2024.2 and later.
