## Why

The OpenSpec plugin's Settings panel already stores a profile field and CLI path, but there is no UI for viewing the active config profile's workflow toggles or switching profiles via the CLI. Additionally, the `openspec update` command (which refreshes agent instruction files) has no IDE integration, forcing users to leave IntelliJ to run it. This is Phase 3 of the v0.3.0 release, filling the last remaining gap between the plugin and the OpenSpec CLI's expanded workflow profile.

## What Changes

- **Config profile section in Settings**: Display the current profile name, its description, and workflow toggles (read-only reflection of CLI-managed config). Users can see which workflows are active under their profile at a glance.
- **Profile switch via CLI delegation**: When the user selects a different profile in Settings, the plugin delegates to `openspec config profile <name>` to update the global config, then refreshes internal state.
- **OpenSpecUpdateAction**: A new action that runs `openspec update` in the background and streams output to the OpenSpec console panel. Provides IDE-native access to agent instruction refresh.
- **Action registration with CLI guard**: The `OpenSpec.Update` action is registered in `plugin.xml` but disabled when `CliDetectionService` reports the CLI is unavailable, with a tooltip explaining the requirement.
- **Tests**: Unit tests for profile settings persistence, profile switch CLI delegation, update action enablement logic, and console output routing.

## Capabilities

### New Capabilities
- `config-profile`: Config profile management — view active profile, workflow toggles, and switch profiles via CLI delegation
- `cli-update`: CLI update action — trigger `openspec update` from the IDE with console output

### Modified Capabilities
- `plugin-core`: Add config profile display and CLI update delegation to the settings and action infrastructure

## Impact

- **Settings UI**: `OpenSpecSettingsPanel` gains a new "Config Profile" section with profile display and workflow toggles
- **Actions**: New `OpenSpecUpdateAction` class extending `OpenSpecCliAction`
- **plugin.xml**: New `OpenSpec.Update` action registration in the main menu group
- **CLI integration**: New CLI commands used: `openspec config profile <name>`, `openspec update`
- **Services**: No new services — uses existing `CliDetectionService`, `CliRunner`, and `OpenSpecSettings`
- **Dependencies**: No new external dependencies
