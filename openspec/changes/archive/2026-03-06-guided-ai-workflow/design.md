## Context

The plugin has a complete AI generation pipeline (Direct API for Claude/OpenAI, clipboard copy, editor tab delivery) and an AI tool detection service that scans for `.claude/`, `.github/`, `.cursor/`, `.windsurf/`, `.cline/` directories. However, none of this is surfaced in the tool window UI. Users see a tree with status icons but have no guidance on what to do next. The generation features are only accessible via right-click context menus that users don't discover. The tool detection results are only shown in Settings as informational text.

The tool window currently has: a tree (Specs/Changes/Archive), a toolbar with action buttons, and a status bar showing CLI status and detected AI tools.

## Goals / Non-Goals

**Goals:**
- Make the "generate next artifact" action visible and one-click from the tool window
- Use AI tool detection to suggest the most appropriate delivery method
- Add Gemini as a third API provider
- Guide new users through AI setup on first use
- Remember the user's preferred delivery method

**Non-Goals:**
- Programmatic integration with Copilot Chat or Gemini IDE plugin (no plugin APIs exist for this)
- Replacing the existing right-click context menu generation (keep it as an alternative)
- Agent/conversational AI (this is one-shot prompt generation)
- Auto-generating without user action (user must click Generate)

## Decisions

**1. Workflow Action Panel as a fixed component below the tree**

Add a panel between the tree and the status bar that shows: the active change name + status, a DAG progress indicator (e.g., "2/4 artifacts"), and a "Generate [artifact-name]" button for the next ready artifact. When no change is active or all artifacts are done, the panel shows contextual guidance ("Create a change to get started" or "All done — ready to apply").

Alternative: Floating toolbar or notification-based approach. Rejected because a persistent panel provides constant visibility without being intrusive, and it's the standard IntelliJ pattern (similar to how VCS panels show branch status).

**2. Smart default delivery method based on detection + history**

When the user clicks "Generate", the delivery method is chosen automatically:
1. If the user has a saved preferred method, use it
2. If no preference saved, check: is a Direct API provider configured with a key? → use Direct API
3. If no API configured, check detected tools: Claude Code detected → default to clipboard with "Copy for Claude Code" label; Copilot detected → "Copy for Copilot Chat"; etc.
4. Fallback → clipboard with generic label

The button has a dropdown chevron (split button) to let the user switch methods. The chosen method is remembered in `OpenSpecSettings.preferredDeliveryMethod`.

Alternative: Always show a dialog asking which method. Rejected because it adds friction on every click. The split button pattern gives one-click default + easy override.

**3. First-run AI setup as a lightweight inline prompt, not a wizard dialog**

On first Generate click, if no preferred method is set and no API is configured, show an inline card in the workflow panel (not a modal dialog) listing detected tools and options. This avoids the jarring modal experience and keeps the user in context.

Alternative: Modal wizard dialog. Rejected because it's heavy for what's essentially a one-time choice. An inline card in the panel is less disruptive.

**4. Gemini API via the same Direct API pattern**

Add `GEMINI` to the `AiProvider` enum with models (`gemini-2.5-pro`, `gemini-2.5-flash`). Add `callGemini()` to `DirectApiService` using the Gemini REST API (`https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`). API key auth is query-parameter based for Gemini. Store credentials via the same `AiCredentialStore`.

Alternative: Use the Vertex AI endpoint. Rejected because the Gemini API is simpler (API key vs service account) and more accessible for individual developers.

**5. Generate button auto-advances after artifact completion**

After direct API generation completes, the panel automatically refreshes the DAG status and shows the next ready artifact. For clipboard/editor modes, the panel shows a "Mark as done" link that the user clicks after they've pasted and saved the AI output, which triggers a re-check.

## Risks / Trade-offs

[Split button is a less common Swing pattern] → IntelliJ has `ActionButton` with popup groups that provide this behavior. Use `SplitButtonAction` or a custom `JPanel` with button + arrow.

[Gemini API key format differs from Claude/OpenAI] → Gemini keys don't have a standard prefix. The test connection feature will validate the key works.

[Detection of `.github/` may be a false positive for Copilot] → Many projects have `.github/` for workflows, not Copilot. Could refine detection to check for `.github/copilot` or Copilot-specific config files, but for now keep it as-is since it's just a suggestion, not a hard decision.

[Inline setup card takes panel space] → It only shows once (until the user picks a method). After that, the panel returns to its normal compact state.
