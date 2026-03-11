## 1. Add ToolGuidance metadata to AiToolDetectionService

- [x] 1.1 Add `ToolGuidance` record to `AiToolDetectionService` with fields: `chatPanelName`, `pasteAction`, `promptPrefix`, `canAutoSave`
- [x] 1.2 Add static `TOOL_GUIDANCE` map with entries for all 6 tools (Claude Code, Gemini, GitHub Copilot, Cursor, Windsurf, Cline)
- [x] 1.3 Add `getToolGuidance(String toolName)` static method that returns guidance for a tool, with a generic fallback for unknown tools

## 2. Update Generate delivery guidance in WorkflowActionPanel

- [x] 2.1 In `showInlineGuidance()` for clipboard delivery, use `ToolGuidance.pasteAction` instead of the generic "Paste into <tool>" text
- [x] 2.2 For IDE panel tools, add the full save path line: "Save the response to: <changeDir>/<outputPath>"
- [x] 2.3 For tools with a `promptPrefix`, add slash command hint line: "Tip: You can also use <prefix><artifactId> directly in <chatPanelName>"
- [x] 2.4 For editor tab delivery, update guidance to reference the tool's `chatPanelName`

## 3. Update Apply delivery guidance in WorkflowActionPanel

- [x] 3.1 In the Apply clipboard delivery handler, use `ToolGuidance.pasteAction` for the action line
- [x] 3.2 For IDE panel tools on Apply, show: "Save tasks.md when the tool finishes working through the tasks"
- [x] 3.3 Keep CLI tool Apply guidance unchanged: "Paste into <tool> — watching tasks.md for progress..."

## 4. Verify

- [x] 4.1 Run `./gradlew clean build` — all green
