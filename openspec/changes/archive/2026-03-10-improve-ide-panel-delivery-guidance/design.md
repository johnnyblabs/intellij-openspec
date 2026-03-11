## Context

When users click Generate or Apply, the plugin copies a prompt to the clipboard and shows inline guidance. Today the guidance is minimal and nearly identical for CLI and IDE panel tools:

- **CLI tools**: "Paste into Claude Code — it will save automatically."
- **IDE panel tools**: "Paste into GitHub Copilot, then save to: proposal.md"

IDE panel users need more help: they need to know *where* to paste (Chat panel, Composer, etc.), whether slash commands are available, and exactly where to save the output file. Each tool has different UI conventions.

## Goals / Non-Goals

**Goals:**
- Provide tool-specific, actionable guidance for each IDE panel tool after Generate/Apply
- Surface available slash commands when the tool supports prompts
- Show the full save path (not just filename) for IDE panel tools
- Keep guidance compact — a few lines, not a tutorial

**Non-Goals:**
- Detecting whether skills/prompts are actually enabled at runtime (not possible)
- Scaffolding missing prompt files (tracked separately as v0.2.0 work)
- Supporting tool-native config formats for Cursor/Windsurf/Cline (v0.3.0)
- Changing how Direct API delivery works

## Decisions

### 1. Tool guidance metadata in AiToolDetectionService

Add a static `ToolGuidance` record to `AiToolDetectionService` that maps each tool to its guidance metadata:

```java
public record ToolGuidance(
    String chatPanelName,      // "Copilot Chat", "Composer", "Chat", etc.
    String pasteAction,        // "Open Copilot Chat and paste", "Open Composer and paste"
    String promptPrefix,       // "/opsx-" for Copilot, "/opsx:" for Claude Code, null for others
    boolean canAutoSave        // true for CLI tools
) {}
```

**Why a record over a map/enum?** Records are type-safe, immutable, and self-documenting. The static map of tool name → ToolGuidance keeps all guidance data in one place.

**Alternative considered:** Putting guidance strings directly in WorkflowActionPanel. Rejected because the panel already has too many responsibilities and guidance data is reusable.

### 2. Three-tier guidance messages

After delivery, show up to three lines:

1. **Status line**: "Copied to clipboard" (already exists)
2. **Action line**: Tool-specific paste instruction (e.g., "Open Copilot Chat and paste the prompt")
3. **Save line**: For IDE panel tools on Generate: "Save the response to: `<full-path>`". For Apply: "The tool will update tasks.md as it works — watching for changes..."

For CLI tools, lines 2-3 collapse to the existing single line since CLI tools handle everything automatically.

**Why not a tooltip/expandable section?** Inline text is immediately visible and requires no interaction. A tooltip would hide critical information behind a hover. Keep it simple for v0.1.0.

### 3. Slash command hints for Generate guidance

When the tool has a `promptPrefix` (Copilot has `/opsx-`, Claude Code has `/opsx:`), add a hint below the guidance:

"Tip: You can also use `/<prefix>propose` directly in <chatPanelName>"

This surfaces the slash commands without requiring users to discover them on their own. Only shown for Generate, not Apply (Apply has no equivalent slash command).

### 4. Apply guidance differentiates CLI vs IDE panel

- **CLI tools**: "Paste into Claude Code — watching tasks.md for progress..." (existing behavior, unchanged)
- **IDE panel tools**: "Open <chatPanelName> and paste the prompt. The tool will work through tasks and update tasks.md — save the file when done."

The key difference: IDE panel tools may not auto-save, so we remind the user.

## Risks / Trade-offs

- **[Stale guidance]** → Tool UIs change (Copilot Chat → Copilot Edits, etc.). Mitigation: guidance text is centralized in one record map, easy to update.
- **[Verbose guidance]** → Three lines may feel noisy for power users. Mitigation: keep text short and direct. Consider adding a "Don't show again" option in a future change.
- **[Incorrect slash command prefix]** → Copilot uses `/opsx-` (dash), Claude uses `/opsx:` (colon). Mitigation: each tool's ToolGuidance record stores its own prefix.
