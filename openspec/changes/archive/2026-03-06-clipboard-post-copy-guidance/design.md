## Context

The WorkflowActionPanel already uses a CardLayout to swap between a "normal" panel and a "setup" card. After clipboard/editor-tab delivery, it currently shows a small "Done — check for updates" link below the Generate button. This link is easy to miss and provides no context about what happened or what to do next.

The `AiToolDetectionService` already detects AI tools (Claude Code, GitHub Copilot, Cursor, Windsurf, Cline, Gemini) and `DeliveryMethodResolver` already resolves tool-aware labels. This infrastructure can be reused to show dynamic guidance.

## Goals / Non-Goals

**Goals:**
- User immediately sees what happened after clicking Generate in clipboard/editor mode
- Guidance is tool-aware — shows the detected AI tool by name
- User knows exactly what to do next (paste, get response, save to path)
- Card is dismissible and doesn't block the workflow

**Non-Goals:**
- Changing the Direct API flow (it already auto-advances)
- Adding new delivery methods
- Monitoring the clipboard or auto-detecting when the user has completed the round-trip

## Decisions

### 1. Replace Generate button area with guidance card after copy

After clipboard/editor-tab delivery, swap the normal panel content to show a guidance card. This is more visible than a small link and clearly communicates state. Use the existing CardLayout mechanism — add a third card ("guidance") alongside "normal" and "setup".

**Alternative considered:** A modal dialog. Rejected because modals are interruptive and the user needs to switch to their AI tool — a persistent but non-blocking card is better.

### 2. Dynamic tool name from AiToolDetectionService

The guidance card calls `AiToolDetectionService.getPrimaryToolLabel()` to get the tool name. If no tools are detected, fall back to generic "your AI tool". This makes the guidance contextual without any new infrastructure.

### 3. Show the output path so users know where to save

The guidance card shows the expected output path (e.g., `specs/<capability>/spec.md`) from the artifact instruction. This eliminates guesswork about where the AI's response should be saved.

### 4. Two actions: "Copy again" and "Check for updates"

- **Copy again** — re-copies the prompt (in case clipboard was overwritten)
- **Check for updates** — dismisses the card, invalidates the DAG cache, and refreshes the panel

## Risks / Trade-offs

- [Risk] Guidance card takes up vertical space → It replaces the Generate button area (same space), and dismisses on check
- [Trade-off] Generic fallback "your AI tool" is vague → But better than no guidance at all; most users will have a detected tool
