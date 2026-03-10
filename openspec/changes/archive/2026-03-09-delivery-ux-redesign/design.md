## Context

The settings panel currently has a tabbed pane with "Tools & Delivery" and "Direct API" tabs. The Tools & Delivery tab shows detected AI tools and a delivery dropdown. The workflow panel has a separate dropdown chevron for switching delivery methods, plus a first-run "setup card" that appears when no delivery preference is saved. This creates three distinct places where delivery is configured or selected, with unclear precedence.

The explore mode (`/opsx:explore`) exists as a prompt for Claude Code and Copilot but has no plugin entry point. Users who want to think through a problem before proposing a change have to manually assemble context.

## Goals / Non-Goals

**Goals:**
1. Single place to select delivery tool — the workflow panel
2. Settings panel becomes simpler (configure, not operate)
3. Tool-first selection: pick "Claude Code" or "Copilot", not "clipboard mode"
4. Add an Explore action that copies project context to clipboard
5. Preserve the remembered preference behavior (last tool selection persists)

**Non-Goals:**
- Adding a chat/conversation panel to the plugin (explore is clipboard-based)
- Changing the Direct API configuration (stays in settings)
- Changing how Generate All works
- Supporting multiple simultaneous delivery targets

## Decisions

### Decision 1: Replace setup card and delivery tab with workflow panel selector

**Choice:** Remove the "Tools & Delivery" tab from settings. Remove the setup card from the workflow panel. Add a compact tool selector dropdown to the workflow panel that's always visible when a change is active.

**Rationale:** The setup card was a first-run workaround for "we don't know your delivery preference." Moving the selector inline makes it a persistent, visible control — no first-run special case needed. The settings tab was redundant with the workflow panel dropdown.

**Alternative rejected:** Keeping the settings tab as a "default" with workflow panel as "override" — confusing mental model, users don't know which one wins.

### Decision 2: Tool-centric dropdown with implicit delivery mode

**Choice:** The dropdown lists tools by name with a type indicator:

```
Claude Code        [CLI]
GitHub Copilot     [IDE]
───────────────────────
Direct API         [API]
───────────────────────
Editor Tab
Clipboard
```

Selecting a tool implicitly sets the delivery mode:
- CLI tools → clipboard (with save-path hint appended)
- IDE_PANEL tools → clipboard
- Direct API → API call
- Editor Tab → editor
- Clipboard → generic clipboard

**Rationale:** Users think in tools, not delivery modes. "I want to use Copilot" is more natural than "I want clipboard mode with Copilot as the target." The delivery mode becomes an implementation detail.

### Decision 3: Flatten settings panel — no tabs

**Choice:** Remove the tabbed pane. Settings layout becomes:
1. OpenSpec CLI section (titled border)
2. General section (titled border)
3. Direct API section (titled border)

All visible at once, no tabs to switch between.

**Rationale:** With the delivery tab removed, there's only one remaining tab (Direct API). A single tab in a tab pane is worse than no tabs. The Direct API section is small enough to display inline.

### Decision 4: Explore action — clipboard with context

**Choice:** Add "Explore..." to the OpenSpec menu. When clicked, it assembles project context (config.yaml, active change info, detected tools, recent specs) into a structured prompt and copies to clipboard. Shows a notification: "Context copied — paste into your AI tool to start exploring."

**Rationale:** Explore is inherently conversational — the plugin can't host a conversation. But it can prepare the conversation by assembling context. This bridges the gap without scope creep.

## Risks / Trade-offs

**Risk:** Users who relied on the settings delivery dropdown need to learn the new workflow panel selector.
→ Mitigation: The workflow panel selector is more discoverable (visible during actual work). The old location was hidden in settings.

**Risk:** Explore action may feel thin — just copying text.
→ Mitigation: It's a v0.1 bridge. The value is in the context assembly, not the clipboard copy. Future versions could integrate with AI tool APIs.

**Risk:** Removing the setup card means first-time users see the tool selector without onboarding.
→ Mitigation: The tool selector pre-selects the first detected tool. If no tools detected and no API configured, show inline help text: "Configure an AI tool or API key to get started."
