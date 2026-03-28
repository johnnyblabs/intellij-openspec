## Context

The plugin's `ExploreContextAction` currently assembles project context via `ExploreContextService` and copies it to the clipboard. The OpenSpec explore workflow (`/opsx:explore`) is an interactive AI thinking partner — it takes an optional topic, investigates the codebase, compares approaches, and can transition into a proposal. The plugin ignores the explore prompt entirely and delivers only raw context, making the IDE experience a downgraded version of the CLI-based explore.

The plugin already has a multi-mode delivery infrastructure (`DeliveryMode.CLIPBOARD`, `EDITOR_TAB`, `DIRECT_API`) used by artifact generation in `WorkflowActionPanel`. The explore action should reuse this infrastructure rather than hardcoding clipboard-only delivery.

## Goals / Non-Goals

**Goals:**
- Rework `ExploreContextAction` to prompt for an optional topic, build the explore prompt (system instructions + project context + topic), and deliver via the user's configured delivery mode.
- For Direct API mode, send the explore prompt to the configured AI provider and display the response in the reworked Explore tab, giving an in-IDE exploration experience.
- For Editor Tab mode, open a scratch file with the full explore prompt + context ready for the user's AI tool.
- For Clipboard mode, copy the full explore prompt + context (not just raw context).
- Rework `ExplorePanel` from a passive context viewer into the home for explore results — showing topic, prompt, and AI response.

**Non-Goals:**
- Multi-turn chat within the IDE. The first iteration delivers a single explore prompt and response. True conversational explore can be added later.
- Adding new AI provider integrations — reuse what `DirectApiService` already supports.

## Decisions

### 1. Reuse the explore skill prompt as the system prompt

The explore prompt text lives in `.claude/commands/opsx/explore.md`. Rather than duplicating or hardcoding this, we read it at runtime from the project's openspec skill files. Fallback: if the skill file doesn't exist (CLI not installed, `openspec update` not run), use a built-in default explore prompt embedded in the plugin.

**Why:** Single source of truth. As the OpenSpec explore skill evolves, the plugin picks up changes automatically after `openspec update`.

**Alternative considered:** Hardcode the prompt in the plugin. Rejected because it drifts from the canonical skill definition.

### 2. Topic input via a lightweight dialog

Show a single-field dialog ("What would you like to explore?") with an optional text input. The user can leave it blank to enter open explore mode, or type a topic/question. This mirrors how `/opsx:explore [topic]` works in the CLI.

**Why:** Minimal friction — one field, optional, with a clear default. No need for a multi-field wizard.

**Alternative considered:** No dialog, just deliver immediately. Rejected because the topic is central to the explore experience — without it, the AI has no starting point.

### 3. Route through delivery modes like artifact generation

Use `DeliveryMethodResolver` to determine the active delivery mode, then:

| Mode | Behavior |
|------|----------|
| **Direct API** | Send the explore prompt to the AI provider via `DirectApiService`, display response in the Explore tab |
| **Editor Tab** | Write the explore prompt to a scratch file and open it |
| **Clipboard** | Copy the explore prompt to clipboard with a notification |

**Why:** Consistent with how the rest of the plugin delivers AI interactions. Users already have a preferred delivery mode configured.

**Alternative considered:** Always use Direct API if configured, clipboard otherwise. Rejected because it overrides user preference and the two-mode fallback is already handled by `DeliveryMethodResolver`.

### 4. Build the prompt as: explore instructions + assembled context + topic

The prompt structure:

```
[Explore skill prompt — the "stance", guardrails, what to do]

---

[Assembled project context from ExploreContextService]

---

Topic: [user's topic, or "Open exploration — what would you like to think about?"]
```

**Why:** The AI needs the explore instructions (to behave as a thinking partner, not just answer questions), the project context (to ground exploration in reality), and the topic (to focus the conversation).

### 5. Rework ExplorePanel to display explore results

Replace the passive context viewer with an explore results panel. The reworked `ExplorePanel` shows:
- A header with the explore topic
- The AI's response (for Direct API delivery)
- Toolbar actions: Refresh (re-run explore), Copy Response, New Explore (re-open topic dialog)

For non-Direct-API deliveries, the panel shows the last assembled prompt with a note about the delivery method used.

**Why:** The Explore tab is the natural home for explore results. The old passive context dump adds no value that the explore prompt assembly doesn't already cover — the assembled prompt includes the full project context. Repurposing the tab gives explore a dedicated space in the tool window.

**Alternative considered:** Display in the Console tab. Rejected because the Console is shared with CLI output and validation results — explore results deserve their own persistent space.

### 6. Add a `generate` overload to DirectApiService for raw prompts

`DirectApiService.generate(ArtifactInstruction)` builds prompts from artifact metadata. Explore needs to send an arbitrary prompt string. Add a `generate(String prompt)` method (or `generate(String systemPrompt, String userMessage)`) that sends a raw prompt to the configured provider.

**Why:** The existing method is tightly coupled to artifact generation. Explore is a different use case that needs the same provider infrastructure but different prompt construction.

## Risks / Trade-offs

- **Prompt file may not exist** → Mitigated by embedding a default explore prompt in the plugin as a fallback constant. The built-in default covers the core explore stance and guardrails.
- **Direct API response can be slow (minutes for long explorations)** → Mitigated by running on a background thread with a progress indicator. The Explore tab displays results when ready. Future: streaming support.
- **Single-turn limitation** → Accepted trade-off for v1. The user gets one explore response per invocation. Multi-turn chat is a future enhancement.
- **Token limits** → The assembled context + explore prompt + topic could be large for projects with many specs/changes. Mitigated by the existing `ExploreContextService` which already produces reasonably-sized context. `DirectApiService` uses 8192 max output tokens.
