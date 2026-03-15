## 1. Extend AiToolDetectionService

- [x] 1.1 Add `getToolStatus(String toolName)` method returning CONFIGURED, DETECTED, or AVAILABLE
- [x] 1.2 Add `getSkillsDir(String toolName)` that maps tool name to expected skills path
- [x] 1.3 Add `getAllToolsWithStatus()` returning a list of tool name + status pairs

## 2. Create ManageAiToolsDialog

- [x] 2.1 Create dialog with `SearchTextField` filter bar at top
- [x] 2.2 Create categorized `JBList` with section headers (Configured, Detected, Available)
- [x] 2.3 Create custom `ListCellRenderer` showing: status icon, tool name, type badge (CLI/IDE), action button
- [x] 2.4 Implement filter logic — case-insensitive substring match on tool name, hide non-matching rows
- [x] 2.5 Implement "Add" / "Configure" action — runs `openspec init --tools <id>` via CliRunner with progress
- [x] 2.6 Implement "Update" action — runs `openspec update` via CliRunner with progress
- [x] 2.7 Add "Refresh" button to re-scan tool status
- [x] 2.8 Show fallback message when CLI unavailable: "Install the OpenSpec CLI to generate skill files"
- [x] 2.9 Synchronous VFS refresh after any tool action

## 3. Register action and access points

- [x] 3.1 Create `OpenSpecManageToolsAction` extending `OpenSpecBaseAction`
- [x] 3.2 Register in plugin.xml under OpenSpec menu (after Setup Wizard)
- [x] 3.3 Add toolbar button in tool window toolbar group (gear icon with "Manage AI Tools" tooltip)
- [x] 3.4 Add "Manage AI Tools..." button to `OpenSpecSettingsPanel`
- [x] 3.5 Add "Manage AI Tools" link on GettingStartedPanel NO_AI_CONFIGURED card
- [x] 3.6 Add gear icon next to tool selector in WorkflowActionPanel that opens the dialog

## 4. Verify

- [x] 4.1 Build plugin and confirm zero compilation errors
