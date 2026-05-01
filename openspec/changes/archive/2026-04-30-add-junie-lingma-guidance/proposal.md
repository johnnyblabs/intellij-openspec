## Why

The recently-archived `add-forgecode-bob-shell-guidance` change deliberately deferred per-tool `TOOL_GUIDANCE` for Junie and Lingma because the IDE-resident panel names weren't confidently sourced. A focused research pass into JetBrains and Alibaba's official docs has now produced authoritative anchors:

- **Junie** uses a slash-command syntax (`/opsx-<name>`) backed by the same `.junie/commands/opsx-<id>.md` files OpenSpec already generates. The panel name is in flux as of Dec 2025 (the standalone "Junie" tool window is being merged into the unified "AI Chat" panel for IDE 2025.3+), but a dual-mention copy works gracefully across both versions.
- **Lingma**'s "AI Chat panel" naming has been stable through 2025; agent and chat modes share the same panel.

Both tools currently fall through to `DEFAULT_GUIDANCE` ("your AI tool" / "Paste into your AI tool") and now that we have stable panel-name signal we can give them tailored copy parallel to GitHub Copilot, Cursor, Cline, and the other IDE-resident entries.

## What Changes

- Add two `TOOL_GUIDANCE` entries to `AiToolDetectionService`:

  ```
  "Junie"  → ToolGuidance("Junie",       "Open Junie and paste the prompt",       "/opsx-", false)
  "Lingma" → ToolGuidance("Lingma chat", "Open Lingma chat and paste the prompt", null,     false)
  ```

  - Both `pasteAction` strings follow the existing IDE-panel convention — `"Open <Panel> and paste the prompt"` — used by every prior entry (Copilot Chat, Composer, Cascade, Cline chat, Kiro chat, Roo Code chat, Continue chat, Amazon Q chat). Users find their own panel from the tool name; the copy doesn't try to disambiguate panel labels across IDE versions.
  - `chatPanelName` and `pasteAction` are aligned to the same panel string within each entry (no contradiction between short vs long names).
  - Junie gets the `/opsx-` prefix matching GitHub Copilot's pattern — documented behavior in JetBrains' Junie slash-command docs. Lingma's `promptPrefix` stays `null` because Alibaba's Lingma docs don't confirm that local `.lingma/commands/opsx/<id>.md` files are auto-discovered as slash commands. (Note: `promptPrefix` is currently set on `ToolGuidance` records but not consumed by any caller — both choices are correct-metadata-for-future-use; neither has runtime impact today.)
  - `canAutoSave: false` for both since they're IDE_PANEL-classified — the prompt is delivered to the panel, not to a terminal, so the autosave-then-paste flow doesn't apply.
- Update tests: replace the `junieAndLingma_stillFallThroughToDefault` regression guard with two per-tool guidance assertions plus a third assertion that some unknown tool name still returns `DEFAULT_GUIDANCE` (preserves coverage of the default-fallback scenario the older test was previously providing).
- Update the `ai-integration` "Default fallback when no explicit entry exists" scenario to remove Junie and Lingma from its example list (they're no longer fallback candidates). The scenario itself stays; only the parenthetical example shrinks.
- Add a `## Unreleased` CHANGELOG entry covering the polish.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: existing `Tool-specific guidance` requirement's "Default fallback when no explicit entry exists" scenario gets a small example-list edit to drop Junie/Lingma. No new scenarios; no contract change.

## Impact

- Code: `src/main/java/com/johnnyblabs/openspec/services/AiToolDetectionService.java` — 2 lines added to `TOOL_GUIDANCE`.
- Tests: `src/test/java/com/johnnyblabs/openspec/services/AiToolDetectionServiceTest.java` — replace 1 test with 2 (one per tool); ~15 LOC delta.
- Spec: `openspec/specs/ai-integration/spec.md` — one example-list edit (drop "Junie, Lingma," from the "Default fallback" scenario's parenthetical).
- Risk: very low. Additive UX polish; the only meaningful failure mode is JetBrains completing the Junie → AI Chat merger to the point where mentioning "Junie" in copy feels stale, which would be a one-line follow-up edit.

### Out of scope

- **Slash-command discovery for Lingma.** The plugin assumes Lingma slash commands work via paste, not file-based discovery, until/unless Alibaba publishes that the Lingma plugin auto-reads `.lingma/commands/opsx/<id>.md`. If they later confirm it, a follow-up can flip Lingma's `promptPrefix` from `null` to `/opsx-`.
- **Following Junie's panel rename.** When/if JetBrains finishes deprecating the standalone "Junie" tool window in favor of "AI Chat" everywhere, simplify the copy to mention only "AI Chat". Not now — the dual-mention is correct during the migration window.
- **Distinguishing Lingma's Ask mode vs Agent mode** in the guidance copy. Both modes share the same panel; the copy is panel-correct in either case.

## References

- Forgejo: johnb/intellij-openspec#197
- Plane: openspec/issue/214 (`3583a34a-6496-4b76-a105-8954f8fa51ea`)
- Related changes: `archive/2026-04-30-add-openspec-1-3-tool-support`, `archive/2026-04-30-add-forgecode-bob-shell-guidance`
- [Junie slash commands docs](https://junie.jetbrains.com/docs/custom-slash-commands.html)
- [Junie merged into AI Chat (JetBrains blog, Dec 2025)](https://blog.jetbrains.com/ai/2025/12/junie-now-integrated-into-the-ai-chat/)
- [Lingma AI Chat overview](https://www.alibabacloud.com/help/en/lingma/user-guide/overview-of-chat)
