## Why

The plugin has AI artifact generation capabilities (Direct API, clipboard copy, editor tab) but they are completely invisible to users. There is no UI guidance after proposing a change — users see a tree with status icons but have no idea how to generate artifacts or that AI can do it for them. The existing delivery mode dialog requires users to already know the feature exists and navigate a multi-step right-click flow. The AI tool detection (Copilot, Claude, Cursor, etc.) is informational only — it doesn't influence behavior. The result: the plugin's core value proposition (structured AI-assisted artifact generation) is unreachable for most users.

## What Changes

- Add a **Workflow Action Panel** to the tool window that shows the active change's progress and a prominent "Generate" button for the next ready artifact
- The Generate button adapts based on detected AI tools — if Claude Code is detected, offer "Run in Claude Code"; if Copilot is detected, offer "Copy for Copilot Chat"; if an API key is configured, offer "Generate Now"
- Add a **first-run AI setup flow** that triggers when the user first tries to generate — it detects available AI tools and helps the user pick their preferred method
- Add **Gemini** as a third API provider alongside Claude and OpenAI
- Streamline the generation flow from 3+ dialogs to a single click using the user's configured/detected preferred method
- Add a **smart default** — remember the user's last-used delivery method and auto-select it
- After each artifact is generated, auto-advance the action panel to show the next ready artifact

## Capabilities

### New Capabilities
- `workflow-panel`: Action panel in the tool window showing active change progress, next-step guidance, and a contextual Generate button
- `ai-setup`: First-run detection and configuration flow that discovers available AI tools and sets the preferred generation method
- `gemini-provider`: Gemini API as a third direct generation provider

### Modified Capabilities
- `tool-window`: Tool window gains the workflow action panel below the tree
- `actions`: Generate action streamlined to one-click with smart defaults

## Impact

- `OpenSpecToolWindowPanel.java` — new workflow action panel component added below the tree
- `OpenSpecToolWindowFactory.java` — panel integration
- `AiProvider.java` — add GEMINI enum value
- `DirectApiService.java` — add Gemini API endpoint support
- `AiToolDetectionService.java` — drive behavior from detection, not just display
- `OpenSpecSettings.java` — add preferred delivery method setting
- `GenerateArtifactAction.java` — streamlined one-click flow with smart defaults
- `settings/OpenSpecSettingsPanel.java` — Gemini provider option and model list
- New: `WorkflowActionPanel.java` — the action bar component
- New: `AiSetupDialog.java` — first-run AI tool configuration
