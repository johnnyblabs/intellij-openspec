## 1. Config Profile Section in Settings

- [x] 1.1 Add `buildConfigProfileSection()` method to `OpenSpecSettingsPanel` with a titled bordered panel displaying profile name, description, and workflow list
- [x] 1.2 Implement async profile detail loading via `CliRunner.run(project, "config", "profile", "--json")` on a pooled thread with `invokeLater` UI update
- [x] 1.3 Parse the JSON response to extract profile name, description, and active workflow names
- [x] 1.4 Display workflow toggles as read-only labels in the Config Profile section
- [x] 1.5 Show "CLI required for profile details" fallback when `CliDetectionService.isAvailable()` is false
- [x] 1.6 Refresh the Config Profile section when the user changes the profile combo box selection

## 2. Profile Switch via CLI Delegation

- [x] 2.1 In `OpenSpecConfigurable.apply()`, detect when the profile field has changed compared to the persisted value
- [x] 2.2 When profile changes and CLI is available, run `openspec config profile <name>` via `CliRunner` before persisting
- [x] 2.3 On CLI success, persist the new profile to `OpenSpecSettings` and refresh the Config Profile section
- [x] 2.4 On CLI failure, show a warning notification with the error message and revert the profile combo box to the previous value
- [x] 2.5 When CLI is unavailable, persist locally and show an informational notification that the change is local-only

## 3. OpenSpecUpdateAction

- [x] 3.1 Create `OpenSpecUpdateAction` extending `OpenSpecCliAction` with `getCliArgs()` returning `{"update"}` and `getCommandLabel()` returning `"update"`
- [x] 3.2 Override `update(AnActionEvent)` to check both OpenSpec project detection and `CliDetectionService.isAvailable()`
- [x] 3.3 When CLI is not detected, set `presentation.setEnabled(false)` and set description to "Install OpenSpec CLI to use this action"
- [x] 3.4 When CLI is detected and project is an OpenSpec project, set `presentation.setEnabled(true)` with the standard description

## 4. Action Registration in plugin.xml

- [x] 4.1 Register `OpenSpec.Update` action in the `OpenSpec.MainMenu` group after the Refresh action with text "Update OpenSpec" and icon `AllIcons.Actions.Download`
- [x] 4.2 Add `<reference ref="OpenSpec.Update"/>` to the `OpenSpec.ToolWindowToolbar` group
- [x] 4.3 Verify the action appears disabled in menus when CLI is not available

## 5. Tests

- [x] 5.1 Add unit test for profile detail JSON parsing (valid response, empty response, malformed JSON)
- [x] 5.2 Add unit test for profile switch logic: CLI success persists new value, CLI failure retains old value
- [x] 5.3 Add unit test for `OpenSpecUpdateAction.update()` enablement: disabled when CLI unavailable, enabled when CLI available, hidden for non-OpenSpec projects
- [x] 5.4 Add unit test for `OpenSpecUpdateAction` CLI args and command label
- [x] 5.5 Add unit test for Config Profile section fallback display when CLI is unavailable
