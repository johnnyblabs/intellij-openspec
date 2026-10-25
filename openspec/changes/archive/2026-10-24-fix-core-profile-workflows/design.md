## Context

`WorkflowProfileService.parseSnapshot` reads `profile` and `workflows` from `openspec config list --json`. It uses the stored list whenever one is present, whatever the profile, and otherwise falls back to `CORE_DEFAULTS = {propose, explore, apply, sync, archive}`.

Upstream's `getProfileWorkflows(profile, customWorkflows)` in `dist/core/profiles.js` behaves differently. Checked in the published packages on 2026-10-24:

| CLI | `CORE_WORKFLOWS` | custom `sync` rule |
|---|---|---|
| 1.3.0, 1.3.1 | propose, explore, apply, archive | no |
| 1.4.0 – 1.5.x | propose, explore, apply, sync, archive | no |
| 1.6.0 – 1.9.x | propose, explore, apply, update, sync, archive | no |
| 1.10.0 – 1.15.0 | same six | yes: insert `sync` before the first `archive` or `bulk-archive` when missing |

For `profile === 'custom'`, upstream returns the stored list, or `[]` when there is none. For every other value, including none, it returns `CORE_WORKFLOWS`.

The detected CLI version is available from `CliDetectionService.getDetectedVersion()`, and `CliVersion.atLeast` compares versions numerically.

## Goals / Non-Goals

**Goals:**
- The plugin's active workflows equal what the detected CLI installs, for every supported version.
- One pure function to test, so the version boundaries are pinned.

**Non-Goals:**
- Writing or repairing the user's global config. The leftover list is harmless to the CLI, and the plugin never edits global config.
- Changing the status bar or Settings UI.
- Profiles other than `core` and `custom`. Upstream has none, and treats any other value as core.

## Decisions

1. **A pure resolver, `WorkflowProfileService.resolve(profile, stored, cliVersion)`.** It mirrors `getProfileWorkflows`:
   - **`custom`:** the stored list, in order. A missing list gives an empty set, as upstream returns `[]`. When `cliVersion` is at least 1.10.0 and the list has `archive` or `bulk-archive` but not `sync`, `sync` is inserted before the first of them.
   - **Anything else:** `coreWorkflows(cliVersion)`. A null version, meaning no CLI, gives the current six.
   - **Profile name:** the name shown is the stored profile, or `core` when there is none, as today.
2. **`coreWorkflows(version)` holds the three generations as constants,** with the boundaries 1.4.0 and 1.6.0. `CORE_DEFAULTS` becomes the current set (`CURRENT_CORE`), which the fallback uses.
3. **The CLI version is read at resolve time** from `CliDetectionService`, not cached separately, so a CLI upgrade picked up by re-detection is reflected on the next refresh.
4. **An empty custom list stays empty,** matching the CLI, which then installs no workflow skills. Today the plugin shows the core set instead, which overstates what's installed. *Alternative:* keep the core fallback for an empty custom list. Rejected, because the plugin shouldn't claim workflows the CLI won't install.
5. **Fixtures are captured, not written by hand.** From the real 1.15.0 CLI with an isolated `XDG_CONFIG_HOME` holding a hand-placed `openspec/config.json`, capture `config list --json` as `fixtures/cli/1.15.0/config-list/{fresh,stale-core,custom-archive-no-sync}.json`. For each case, also capture the human `config list` output and the workflows `init --tools claude` actually installs. The installed list is the oracle the resolver must match: capture showed that even the human output leaves out the `sync` that custom archiving pulls in. Record the capture recipe in the fixtures README.

## Risks / Trade-offs

- **Users on a stale core config** will see their workflow count go from 4 to 6, and `update`-gated actions enable. → That's the fix, and it matches what the CLI installed.
- **An empty custom list now shows no workflows,** and the workflow-bound actions become disabled. → That's what the CLI installs. The tooltip guidance already explains disabled workflows.
- **Version unknown with a CLI present** (an unparseable version string). → `CliVersion.atLeast` returns false for null or empty input, so the oldest core set would apply. To avoid that, a blank or unparseable version is treated as "no version" and gets the current set.
