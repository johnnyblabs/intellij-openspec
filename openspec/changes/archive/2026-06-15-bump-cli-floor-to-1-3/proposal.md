## Why

The plugin's minimum-supported OpenSpec CLI floor was raised from 1.2.0 to 1.3.0 in commit `5e83163` ("Raise minimum supported OpenSpec CLI to 1.3.0") without an OpenSpec change proposal. The constant in `SchemaService.MIN_CLI_VERSION`, the README, and `plugin-core`'s floor-notification requirement all reflect 1.3.0 today, but `schema-management`'s "CLI version guard" requirement still says **1.2.0** — stale. The next release explicitly supports CLI **1.3.x and 1.4.x**; 1.2.x and earlier are below the supported floor. This change retro-documents that contract, aligns the stale spec, and trims the Settings version-override combo to match.

## What Changes

- Lock in CLI 1.3.0 as the minimum supported floor in the `schema-management` spec language (currently says 1.2.0 — out of sync with shipped code).
- Document the "1.3.x floor + 1.4.x recommended" support model so both versions are first-class throughout the spec layer, not just in README copy.
- Trim `OpenSpecSettingsPanel.versionCombo` presets from `["", "1.0.0", "1.1.0", "1.2.0", "1.3.0", "1.4.0"]` to `["", "1.3.0", "1.4.0"]`. The combo stays `setEditable(true)` so a legacy user with `version: 1.2.0` in their config.yaml can still type it — presets are a convenience, not a constraint.
- Fix the `schemaUnsupportedLabel` text in `OpenSpecSettingsPanel.java` from `"Schema management requires OpenSpec CLI v1.2.0+"` to `"v1.3.0+"`. The label drifted out of sync with `MIN_CLI_VERSION` when commit `5e83163` raised the floor without updating the user-facing copy — a user on CLI 1.2.x currently sees a "you're OK" message even though schema-management features are gated off.
- No `MIN_CLI_VERSION` constant change — it's already at 1.3.0.
- No code changes beyond the combo trim and the label text fix.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `schema-management`: the CLI version guard requirement currently floors at 1.2.0; bump the spec text to 1.3.0 to match shipped code.

## Impact

- **User-visible**: Settings panel's "Version override" combo no longer offers `1.0.0`, `1.1.0`, or `1.2.0` as preset values. Users who had picked one of those will still see it persisted (combo is editable, so the stored value renders); they just won't find it in the dropdown next time. The plugin already shows the "older than 1.3.0" notification for those CLI versions (per `plugin-core`'s existing floor-notification requirement), so the UX message is consistent: pre-floor versions are documented as below-floor everywhere.
- **Code**: One-line change to `OpenSpecSettingsPanel.versionCombo` initializer. No other source changes.
- **Specs**: One delta spec under `schema-management`. No changes to `plugin-core` or `validation` — already at 1.3.0.
- **Tests**: `SchemaServiceTest`'s `unsupported_whenVersionIs_1_2_0_belowNewFloor` already pins the boundary at 1.3.0 (added in `5e83163`'s test suite). Verify it still passes after the spec text update; no new tests needed.
- **Compatibility**: Plugin continues to support IntelliJ IDEA 2024.2+. No platform-side impact.
- **Trackers**: Will be mirrored via `/mirror-change-trackers bump-cli-floor-to-1-3` after this proposal lands. The earlier `bump-cli-floor-to-1-4` proposal (a tracker entry) was closed as deferred — it returns when enough of the install base has moved past 1.3.x to make the next bump worthwhile, paired with the V1_X axis discussion that was extracted from it.
- **Tangentially-related work already on main**:
  - Commit `f2303ba` — added 1.3.0/1.4.0 to the version-override combo presets + CLI-failure notification on profile switch. This change builds on `f2303ba` by trimming the *pre-floor* entries.
  - Commit `c34c7b2` — removed dead `version:` key from this project's `openspec/config.yaml`. Unaffected.