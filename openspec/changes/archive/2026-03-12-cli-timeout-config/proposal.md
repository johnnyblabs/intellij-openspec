## Why

CLI commands had a hardcoded 30-second timeout. Users with slow networks or large projects had no way to adjust this.

## What Changes

- Add `cliTimeoutSeconds` field to `OpenSpecSettings.State` with default of 30
- Add `JSpinner` control in Settings → Tools → OpenSpec for CLI timeout (1-3600 seconds)
- Wire `OpenSpecConfigurable` for `isModified`/`apply`/`reset` of the new setting
- `CliRunner.run()` reads timeout from settings instead of using hardcoded constant

## Capabilities

### New Capabilities
_(none)_

### Modified Capabilities
- `settings-panel-sections`: Add CLI timeout configuration field

## Impact

- **Files modified**: `OpenSpecSettings.java`, `OpenSpecSettingsPanel.java`, `OpenSpecConfigurable.java`, `CliRunner.java`
- **Risk**: Low — additive setting with sensible default
