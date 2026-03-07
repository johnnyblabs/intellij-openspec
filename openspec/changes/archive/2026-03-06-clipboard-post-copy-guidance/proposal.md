## Why

When the user clicks Generate in clipboard mode, the prompt is silently copied and a small "Done — check for updates" link appears. The user gets no feedback about what happened, no guidance on what to do next, and no indication of which AI tool to paste into. First-time users are completely lost — they don't know a prompt was copied, where to paste it, or what to do with the AI's response. The workflow is invisible.

## What Changes

- After a clipboard or editor-tab generation, replace the Generate button area with a visible guidance card showing: confirmation of what happened, the detected AI tool name to paste into, the expected output path, and clear next-step instructions
- Make the guidance card dynamic — it uses `AiToolDetectionService.getPrimaryToolLabel()` to show "Paste into Claude Code" vs "Paste into GitHub Copilot" vs "Paste into Gemini" based on detected tools
- Provide "Copy again" and "Check for updates" buttons on the guidance card
- The guidance card dismisses when the user clicks "Check for updates" or when the artifact status changes to done

## Capabilities

### New Capabilities

### Modified Capabilities
- `workflow-panel`: Add post-generation guidance card that replaces the Generate button area after clipboard/editor delivery, showing dynamic tool-aware next steps

## Impact

- `WorkflowActionPanel.java` — add guidance card panel with CardLayout state, wire it into `executeGeneration()` for CLIPBOARD and EDITOR_TAB modes
- `AiToolDetectionService.java` — no changes needed, already provides `getPrimaryToolLabel()`
- `DeliveryMethodResolver.java` — no changes needed, already provides tool-aware labels
