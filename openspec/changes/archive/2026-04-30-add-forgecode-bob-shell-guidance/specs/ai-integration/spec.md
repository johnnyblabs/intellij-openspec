## MODIFIED Requirements

### Requirement: Tool-specific guidance

The plugin SHALL provide tool-specific delivery guidance (chat panel name, paste instructions, slash command prefix) for each detected AI tool. Tools classified as `IDE_PANEL` SHALL receive panel-specific copy ("Open <Panel> and paste the prompt"); tools classified as `CLI` SHALL receive terminal-paste copy ("Paste into <Tool>"). Tools without an explicit `TOOL_GUIDANCE` entry SHALL fall back to a generic default.

#### Scenario: Post-delivery guidance
- **WHEN** the user generates via clipboard or editor for an IDE panel tool
- **THEN** the plugin SHALL display tool-specific instructions (e.g., "Open Copilot Chat and paste the prompt")

#### Scenario: Terminal CLI tool guidance
- **WHEN** the user generates via clipboard or editor for a tool classified as `CLI` that has an explicit `TOOL_GUIDANCE` entry (e.g., Claude Code, Gemini, Codex, OpenCode, ForgeCode, Bob Shell)
- **THEN** the plugin SHALL display "Paste into <Tool>" copy and identify the chat-panel name as "terminal"

#### Scenario: ForgeCode and Bob Shell explicit guidance
- **WHEN** the user generates for ForgeCode or Bob Shell
- **THEN** the lookup SHALL return a `ToolGuidance` with `chatPanelName == "terminal"` and `pasteAction == "Paste into ForgeCode"` or `"Paste into Bob Shell"` respectively, NOT the `DEFAULT_GUIDANCE` placeholder

#### Scenario: Default fallback when no explicit entry exists
- **WHEN** the user generates for a tool with no `TOOL_GUIDANCE` entry (e.g., Junie, Lingma, or any future tool not yet wired up)
- **THEN** the plugin SHALL return `DEFAULT_GUIDANCE` ("your AI tool" / "Paste into your AI tool") rather than throwing or returning null
