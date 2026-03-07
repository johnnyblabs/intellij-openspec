## 1. Settings and Detection

- [x] 1.1 Add `preferredTool` field to `OpenSpecSettings.State` (default empty string), with getter/setter
- [x] 1.2 Add tool type classification to `AiToolDetectionService`: static map of tool name → ToolType enum (CLI, IDE_PANEL)
- [x] 1.3 Add `getPreferredToolLabel()` method to `AiToolDetectionService` that returns `preferredTool` from settings if set, otherwise first detected tool
- [x] 1.4 Add `isCliTool(String toolName)` method to `AiToolDetectionService` that returns whether the tool is CLI-based

## 2. Setup Card Tool Selector

- [x] 2.1 In `WorkflowActionPanel.createSetupCard()`, add a tool selector combo box when multiple tools are detected
- [x] 2.2 When a delivery method is selected from the setup card, also persist the selected tool to `OpenSpecSettings.preferredTool`
- [x] 2.3 When single tool detected, auto-set `preferredTool` without showing selector

## 3. Tool-Specific Guidance

- [x] 3.1 Update `showGuidanceCard()` to use `AiToolDetectionService.getPreferredToolLabel()` instead of `getPrimaryToolLabel()`
- [x] 3.2 Update guidance text to be tool-type-aware: CLI tools get "it will save the output automatically" messaging; IDE panel tools get "copy the response and save to" messaging
- [x] 3.3 For CLIPBOARD mode with CLI tools, append save-path hint line to the prompt before copying: "Save your response to: [changeDir]/[outputPath]"

## 4. Verification

- [x] 4.1 Write unit tests for tool type classification (Claude Code → CLI, Copilot → IDE_PANEL, unknown → IDE_PANEL fallback)
- [x] 4.2 Verify the plugin builds and all tests pass
