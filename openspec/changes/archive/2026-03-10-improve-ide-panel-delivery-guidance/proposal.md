## Why

When the plugin delivers prompts to IDE panel tools (Copilot, Cursor, Windsurf, Cline), the guidance text is generic and unhelpful. CLI tools get "Paste into Claude Code — it will save automatically," but IDE panel tools get the nearly identical "Paste into GitHub Copilot — watching tasks.md for progress..." with no actionable steps for the user. IDE panel users don't know *where* to paste (Chat panel? Composer? Inline?), *how* to save the output, or *what the slash commands are* (e.g., `/opsx-propose`). This is the most common delivery path for v0.1.0 users and needs to feel polished.

## What Changes

- Add tool-specific step-by-step guidance for each IDE panel tool when prompts are delivered via clipboard or editor tab
- Show the user available slash commands (e.g., `/opsx-propose`) when the tool supports prompts
- Add a "save to" reminder with the actual file path for IDE panel tools (they can't auto-save)
- Differentiate between Generate (artifact output) and Apply (tasks.md) guidance since they have different save targets
- Include a brief "How to use" tooltip or expandable section in the workflow panel guidance area

## Capabilities

### New Capabilities
- `tool-delivery-guidance`: Tool-specific delivery guidance messages and step-by-step instructions for IDE panel tools

### Modified Capabilities
- `apply-task-delivery`: Add IDE-panel-specific guidance for Apply delivery (save target, manual steps)
- `delivery-aware-button`: Add tool-specific guidance text that appears after clicking Generate

## Impact

- `WorkflowActionPanel.java` — guidance label text and showInlineGuidance method
- `AiToolDetectionService.java` — add method to return tool-specific guidance metadata (prompt command prefix, chat panel name, etc.)
- `DeliveryMethodResolver.java` — may need to surface tool capabilities alongside resolved method
