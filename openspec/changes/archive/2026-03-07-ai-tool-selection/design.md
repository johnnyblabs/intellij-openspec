## Context

`AiToolDetectionService` detects tools by scanning for config directories (`.claude/`, `.github/`, `.cursor/`, etc.) and returns an ordered list. `getPrimaryToolLabel()` returns the first one found. The guidance card and `DeliveryMethodResolver` both call this to get a tool name for labels.

`OpenSpecSettings` already persists `preferredDeliveryMethod` (CLIPBOARD/EDITOR_TAB/DIRECT_API). The setup card in `WorkflowActionPanel` shows delivery method options on first use. There's no concept of "which AI tool am I targeting" — only "how do I deliver the prompt."

Different tools have different round-trip workflows:
- **CLI tools** (Claude Code, Gemini CLI): Can read files and write output directly. Prompt can include "save to <path>" and the tool will do it.
- **IDE panel tools** (GitHub Copilot, Cursor, Cline): User pastes into a chat panel, reads the response, and manually saves. Need explicit "copy the response and save to <path>" instructions.

## Goals / Non-Goals

**Goals:**
- Let users pick which detected AI tool they're targeting
- Persist the selection across sessions
- Show tool-specific guidance after generation (different instructions for CLI vs IDE tools)
- Wrap clipboard prompts with tool-aware context (save-path hint for CLI tools)

**Non-Goals:**
- Adding new AI tool detection (current set of 6 tools is sufficient)
- Integrating with tool APIs or UIs (just improving labels and instructions)
- Changing Direct API behavior (only affects CLIPBOARD and EDITOR_TAB modes)

## Decisions

### 1. Add `preferredTool` to OpenSpecSettings

A new `preferredTool` string field persists the user's selected tool name (e.g., "Claude Code", "GitHub Copilot"). Default is empty string — when empty, fall back to `AiToolDetectionService.getDetectedTools().get(0)` (current behavior).

### 2. Categorize tools as CLI-based vs IDE-panel

Add a static map in `AiToolDetectionService` that classifies each tool:
- **CLI**: Claude Code, Gemini (these run in terminal, can write files)
- **IDE Panel**: GitHub Copilot, Cursor, Windsurf, Cline (chat panels, manual save)

This classification drives guidance text and prompt wrapping.

**Alternative considered:** Per-tool templates with full custom text. Rejected as over-engineered — the CLI vs IDE distinction covers the meaningful differences.

### 3. Tool selector in setup card

When the setup card shows on first use and multiple tools are detected, add a combo box or button group to pick the target tool before selecting the delivery method. The selected tool is saved to `preferredTool`. Single-tool projects auto-select.

### 4. Tool-specific guidance text in the guidance card

After clipboard delivery, the guidance card currently shows:
> "Paste into [tool], then save the response to: [path]"

With tool classification:
- **CLI tool**: "Paste into Claude Code — it will save the output automatically" + path shown as reference
- **IDE panel tool**: "Paste into GitHub Copilot Chat, copy the response, and save to:" + path

### 5. Append save-path hint to clipboard prompt for CLI tools

For CLI-based tools, append a line to the generated prompt:
> "Save your response to: [changeDir]/[outputPath]"

CLI tools like Claude Code can act on this instruction. IDE panel tools ignore it (user manually saves), but it's harmless context.

## Risks / Trade-offs

- [Risk] Tool classification is static and may not match all versions → Mitigation: Classification is a best-effort hint for UX text, not a functional requirement. Wrong classification just means slightly less optimal guidance text.
- [Trade-off] Appending save-path to prompts adds a line to every clipboard prompt → It's a single line, provides useful context for any tool, and helps users even if the tool doesn't auto-act on it.
