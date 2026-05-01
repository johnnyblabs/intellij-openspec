## Context

The prior change `add-forgecode-bob-shell-guidance` shipped tailored `TOOL_GUIDANCE` for ForgeCode and Bob Shell while explicitly deferring Junie and Lingma — both IDE-resident tools whose authoritative panel-name signal hadn't been verified at the time. A focused research pass into JetBrains' Junie docs and Alibaba's Lingma docs has now produced confident anchors for both. This change closes the deferral.

The behavior contract (the `Tool-specific guidance` requirement in `ai-integration`) doesn't change. Only the `TOOL_GUIDANCE` map and one scenario's example list shift to reflect that Junie and Lingma now have explicit entries instead of being fallback examples.

## Goals / Non-Goals

**Goals:**
- Junie and Lingma surface tailored "open <panel> and paste" copy via `getToolGuidance(...)` instead of the generic default.
- Junie's slash-prefix matches its documented `/opsx-<name>` convention, parallel to GitHub Copilot.
- The spec stays accurate: the "Default fallback" scenario no longer cites Junie/Lingma as examples.

**Non-Goals:**
- Following JetBrains' "Junie tool window → AI Chat" merger to its conclusion. We bridge the migration window with dual-mention copy now; revise to single-mention later if/when the standalone tool window is fully removed.
- Confirming Lingma's auto-discovery of `.lingma/commands/opsx/<id>.md` files. Until Alibaba publishes that, the prompt prefix stays `null` and the user pastes the prompt verbatim.
- Per-mode guidance for Lingma (Ask vs Agent). Both modes share the same panel; one copy fits both.

## Decisions

### Decision 1: Match the existing IDE-panel copy convention exactly

**Choice:** `pasteAction = "Open Junie and paste the prompt"` (matching `"Open <Panel> and paste the prompt"`, the convention used by every existing IDE-resident entry). No panel-version disambiguation in the string itself.

**Rationale:** JetBrains is mid-migration on Junie's panel labeling — the standalone "Junie" tool window is being merged into the unified "AI Chat" panel for IDE 2025.3+ (Dec 2025). An earlier draft of this design tried to bridge the migration with `"Open the Junie or AI Chat tool window and paste the prompt"`, but that copy reads awkwardly to both populations: a 2025.2 user sees "Junie" in their UI and is confused by the "or AI Chat" mention, while a 2025.3+ user sees "AI Chat" and wonders what "Junie" is. Users in either version know how to find the AI surface their IDE exposes; the copy doesn't need to spell out the panel label. The shorter form trusts the user's recognition of their own UI and stays consistent with the rest of the registry.

**Alternatives considered:**
- Dual mention `"Open the Junie or AI Chat tool window..."`. Rejected as above.
- `"Open AI Chat and paste the prompt"`. Rejected: stale for users on 2025.2 and earlier, where the panel is still labeled "Junie".
- Detect IDE version and pick dynamically. Rejected: complexity for a one-string difference, and detection adds runtime risk for a copy that's already understandable from tool name alone.

### Decision 2: Align `chatPanelName` and `pasteAction` per entry

**Choice:** For each entry, `chatPanelName` and `pasteAction` reference the same panel string — no contradiction between short and long forms.

- Junie: `chatPanelName="Junie"`, `pasteAction="Open Junie and paste the prompt"` — both reference "Junie".
- Lingma: `chatPanelName="Lingma chat"`, `pasteAction="Open Lingma chat and paste the prompt"` — both reference "Lingma chat".

**Rationale:** A prior draft had `chatPanelName="Lingma chat"` paired with `pasteAction="Open the Lingma AI Chat panel..."` — the two strings used different vocabularies for the same panel. If both surface in the UI (status bar shows the short, popover shows the long), users would see contradictory labels. Aligning them removes the ambiguity. The convention `"<Tool> chat"` matches every other IDE-resident entry's naming (Cline chat, Roo Code chat, Continue chat, Amazon Q chat, Kiro chat).

### Decision 3: Junie gets `/opsx-` prefix; Lingma stays null

**Choice:**
- Junie: `promptPrefix = "/opsx-"` (parallel to GitHub Copilot's pattern).
- Lingma: `promptPrefix = null`.

**Rationale:** JetBrains' Junie slash-command docs explicitly document that `.junie/commands/opsx-foo.md` becomes a `/opsx-foo` chat command via frontmatter discovery. That's the same mechanism GitHub Copilot uses; matching the prefix gives users a consistent experience.

For Lingma, the "AI Chat panel" supports `/` slash commands typed into the chat input, but Alibaba's official docs describe slash commands as configured through their cloud console, not auto-discovered from local files. The `.lingma/commands/opsx/<id>.md` path OpenSpec writes to is community convention, not a documented Lingma feature. Without confirmation, prefixing prompts with `/opsx-` could result in Lingma treating the text as an unrecognized command rather than a literal prompt.

**Alternatives considered:**
- Apply `/opsx-` to both for consistency. Rejected: optimistic prefixing for Lingma risks broken UX. Better to deliver a known-working paste than a possibly-broken slash command.
- Apply `null` to both. Rejected: Junie's slash-command discovery is well-documented; throwing that away costs UX parity with GitHub Copilot for no risk-management benefit.

### Decision 4: `chatPanelName = "Lingma chat"`, not "Lingma AI Chat"

**Choice:** Use `"Lingma chat"` for both `chatPanelName` and `pasteAction`, even though Alibaba's docs call the panel "AI Chat panel".

**Rationale:** Every other IDE-resident tool in `TOOL_GUIDANCE` follows the convention `"<Tool name> chat"` — `Cline chat`, `Roo Code chat`, `Continue chat`, `Amazon Q chat`, `Kiro chat`. Calling Lingma's panel "Lingma AI Chat" would break the visual rhythm of the registry without telling the user anything they don't already know from the tool name. (See Decision 2 for the alignment rationale: the longer form `"Lingma AI Chat panel"` was an earlier draft that contradicted the short `chatPanelName`.)

### Decision 5: Edit the existing "Default fallback" scenario rather than retire it

**Choice:** The "Default fallback when no explicit entry exists" scenario stays in place; only its parenthetical example list changes from "(e.g., Junie, Lingma, or any future tool not yet wired up)" to "(e.g., any future tool not yet wired up)".

**Rationale:** The fallback contract is still a real requirement — `getToolGuidance(...)` must return `DEFAULT_GUIDANCE` rather than throwing or returning null when given an unknown tool name. Removing the scenario would lose that guarantee from the spec. Trimming the example list is the smallest accurate edit.

## Risks / Trade-offs

- **Risk:** JetBrains completes the AI Chat merger and the panel label "Junie" disappears entirely. → Mitigation: low impact because our copy says `"Open Junie and paste the prompt"` rather than naming a specific tool window — users find their AI surface from the tool identity. If JetBrains drops Junie branding entirely, the copy stays usable; only `chatPanelName="Junie"` would feel stale, and that's a one-line follow-up.
- **Risk:** Lingma's plugin actually does auto-discover `.lingma/commands/opsx/<id>.md` and we're being overly conservative about the prefix. → Mitigation: a follow-up flips `null` → `/opsx-` in two characters once Alibaba confirms or a user reports it works. The current copy isn't wrong; it's just less optimized.
- **Trade-off:** Trusting users to recognize their own AI panel rather than spelling out version-specific labels in the copy. We accept this as the right trade-off; matching the existing IDE-panel copy convention is more important than disambiguating panel names the user already sees in their IDE.

## Migration Plan

- No migration. Two map entries + one example-list trim + one test class update.
- Rollout: ships in the next plugin patch release.
- Rollback: revert the commit; both entries are isolated.

## Open Questions

- Will JetBrains finish removing the standalone Junie tool window in 2026? If yes, this change's `pasteAction` becomes stale — but easy to update.
