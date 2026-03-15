## Why

After initial setup, users can't add or manage AI tools. If someone starts with Claude and later wants to add Copilot, there's no way to do it from the plugin — they'd have to run `openspec init --tools` manually in the terminal. The wizard's tool selector is a one-time thing and was previously disabled when no tools were detected.

## What Changes

- New "Manage AI Tools" dialog accessible from Settings, OpenSpec menu, and workflow panel
- Scans project for tool directories AND OpenSpec skill files to determine three states: Configured, Detected, Available
- Filter/search bar for quick tool lookup across all 24 supported tools
- Add/Configure/Update actions delegate to CLI (`openspec init --tools`, `openspec update`)
- Built-in fallback creates tool directory when CLI is unavailable
- Fix wizard tool selector (already done): always enabled, shows all tools

## Capabilities

### New Capabilities
_None — this extends the existing AI integration capability_

### Modified Capabilities
- `ai-integration`: Adding tool management beyond initial setup

## Impact

- New `ManageAiToolsDialog.java` — the dialog with search, categorized tool list, and actions
- New `OpenSpecManageToolsAction.java` — menu action to open the dialog
- `AiToolDetectionService.java` — add methods to check skill file presence per tool
- `plugin.xml` — register new action in OpenSpec menu
- `OpenSpecSettingsPanel.java` — add "Manage AI Tools..." button
