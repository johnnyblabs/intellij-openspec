## Why

When multiple AI tools are detected (e.g., Claude Code and GitHub Copilot), the plugin arbitrarily picks the first one for labeling and guidance. The user can't say "I'm working with Copilot right now" — the guidance card always says "Paste into Claude Code" because `.claude/` is detected first. Worse, the post-generation guidance is generic regardless of which tool you're targeting. Claude Code can save files directly; Copilot Chat requires you to copy the response and save it manually. These are fundamentally different workflows that deserve different instructions.

## What Changes

- Add a `preferredTool` setting to `OpenSpecSettings` that persists the user's selected AI tool
- Add a tool selector to the setup card so users can pick which detected tool they're targeting
- Update `AiToolDetectionService.getPrimaryToolLabel()` to return the preferred tool instead of the first detected
- Add tool-specific guidance text to the post-generation guidance card: different instructions for CLI-based tools (Claude Code, Gemini CLI) vs IDE-panel tools (Copilot, Cursor)
- Add tool-specific context wrapping to the clipboard prompt: append save-path instructions for tools that can act on them

## Capabilities

### New Capabilities

### Modified Capabilities
- `ai-setup`: Setup card shows tool selector when multiple AI tools are detected, persists selection
- `workflow-panel`: Guidance card shows tool-specific next-step instructions based on selected tool

## Impact

- `OpenSpecSettings.java` — add `preferredTool` field to persisted state
- `AiToolDetectionService.java` — `getPrimaryToolLabel()` respects preferred tool setting
- `WorkflowActionPanel.java` — setup card adds tool picker; guidance card uses tool-specific instructions
- `DeliveryMethodResolver.java` — label uses preferred tool name
