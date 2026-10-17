## Why

Settings → Tools → OpenSpec → **Version override** offers only blank or `1.2.0`. It overrides the *config-format* version of `openspec/config.yaml`, not the CLI or a schema version. The plugin models exactly one config format (`VersionSupport.V1_2`, deliberately pinned at 1.2.0), so every value, typed ones included, resolves to the same format, and the control has no observable effect.

It confused users in a sandbox pass, and the docs describe it wrongly. `docs/feature-reference.md` and the Copilot getting-started guide call it "Force a specific OpenSpec schema version", and the comparison matrix lists "Schema version override: Yes" as a feature.

Tracked in the linked tracker entry.

## What Changes

- **Remove the Version override control** from Settings (`OpenSpecSettingsPanel`, `OpenSpecConfigurable`).
- **Stop applying a stored override.** `getEffectiveVersion` resolves from `openspec/config.yaml`'s `version:` (or the 1.2.0 default) as before, but no longer from a value saved by an earlier plugin version, because that value would apply invisibly with no way to see or clear it. The `OpenSpecSettings.versionOverride` field stays, so stored settings still load.
- **Kept on purpose:** the `config.yaml` `version:` reader, `getEffectiveVersion`, and the `VersionSupport` config-format axis. They are the hook for a future format bump, per the project's guidance on plugin-internal config fields. If a second config format ever exists, an override with real choices can come back.
- **Docs:** remove the control from `docs/feature-reference.md` and `docs/getting-started-copilot.md`, remove the "Schema version override" row from `docs/feature-comparison-matrix.md`, and add a CHANGELOG entry.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `workflow-schema-context`: no version-override UI while the config-format axis models one format; a stored override is ignored.

## Impact

- **Code:**
  - `OpenSpecSettingsPanel`: removes the combo, presets and accessors.
  - `OpenSpecConfigurable`: drops `isModified`/`apply`/`reset` handling of the override.
  - `OpenSpecSettings.getEffectiveVersion`: no override layer.
- **Tests:**
  - the effective version resolves to the config's `version:` or 1.2.0, and ignores a stored override;
  - the Settings panel has no version control;
  - existing tests of the override accessors updated deliberately.
- **Docs:** CHANGELOG, feature reference, comparison matrix, and the Copilot getting-started guide.
