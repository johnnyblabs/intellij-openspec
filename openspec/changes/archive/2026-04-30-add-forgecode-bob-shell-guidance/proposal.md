## Why

The just-shipped change `add-openspec-1-3-tool-support` adds Junie, Lingma, ForgeCode, and Bob Shell to the registry. Detection works, but two of those — ForgeCode and Bob Shell — are CLI tools that fall through to `DEFAULT_GUIDANCE` ("your AI tool" / "Paste into your AI tool"), while every other CLI-classified tool (Claude Code, Gemini, Codex, OpenCode) has tailored `TOOL_GUIDANCE` directing the user to paste into the terminal. The two new CLI tools should match the existing pattern. This is also the right moment to spec the implicit "terminal CLI tool" guidance behavior, which has been true since Claude Code was added but was never captured as a scenario.

Junie and Lingma stay on `DEFAULT_GUIDANCE` for now: both are JetBrains/IDE-resident plugins with no public CLI; the plugin can't surface a stable "open X panel and paste" instruction without inventing one. Defer their per-tool guidance until the panel names are confirmed in real-world use.

## What Changes

- Add two `TOOL_GUIDANCE` entries to `AiToolDetectionService`:

  ```
  "ForgeCode" → ToolGuidance("terminal", "Paste into ForgeCode", null, true)
  "Bob Shell" → ToolGuidance("terminal", "Paste into Bob Shell", null, true)
  ```

  Same shape as the existing Codex/OpenCode entries: terminal panel, "Paste into <Tool>" copy, no prompt prefix, autosave-capable.
- No change to TOOL_DIRS / TOOL_TYPES / CLI_TOOL_IDS — those were finalized in the prior change.
- Add a unit test asserting both new guidance lookups return the expected `ToolGuidance` (and aren't falling through to `DEFAULT_GUIDANCE`).
- Spec: add a "Terminal CLI tool guidance" scenario to the existing `ai-integration` "Tool-specific guidance" requirement. This captures the implicit-but-unspec'd behavior: when the user generates for a tool classified as `CLI`, the guidance directs them to paste into the terminal. Existing IDE-panel scenario stays intact.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: existing `Tool-specific guidance` requirement gains one new scenario covering terminal-based CLI tools. Existing IDE-panel scenario unchanged.

## Impact

- Code: `src/main/java/com/johnnyblabs/openspec/services/AiToolDetectionService.java` — 2 lines added to `TOOL_GUIDANCE`.
- Tests: `src/test/java/com/johnnyblabs/openspec/services/AiToolDetectionServiceTest.java` — small new test class (or reuse an existing nested class) asserting the two lookups.
- Spec: `openspec/specs/ai-integration/spec.md` gains one scenario under `Tool-specific guidance` (via this change's delta).
- Risk: very low. Additive UX polish; falls back to `DEFAULT_GUIDANCE` cleanly if reverted.

### Out of scope

- **Per-tool guidance for Junie and Lingma.** Both are IDE-resident; their accurate panel names ("Junie tool window"? "Lingma chat"?) need real-world confirmation. `DEFAULT_GUIDANCE` is a fine fallback until then.
- **`promptPrefix` decoration for ForgeCode and Bob Shell.** Bob Shell's upstream adapter uses `opsx-<id>` command files, suggesting a possible `/opsx-` prefix, but the plugin would need to verify Bob Shell actually accepts slash-prefixed input before committing to it. Defer to a polish change if anyone reports the prefix-less guidance feels wrong.
- **Refactoring `TOOL_GUIDANCE` to live alongside `TOOL_DIRS`/`TOOL_TYPES`/`CLI_TOOL_IDS` as a fourth parallel map.** A future "merge into a single ToolMetadata record" refactor would absorb this, but this change keeps the existing structure.

## Dependencies

- This change depends on the merged state of `add-openspec-1-3-tool-support` (which adds "ForgeCode" and "Bob Shell" to TOOL_TYPES). Apply this change *after* that PR merges to `main`; otherwise the new TOOL_GUIDANCE entries reference display names that don't yet exist in the upstream `TOOL_TYPES` map.

## References

- Tracker: the linked issue
- Plane: openspec/issue/213 (`fd16aba3-f41f-443e-8771-6d9f463834e4`)
- Related change: `archive/2026-04-30-add-openspec-1-3-tool-support`
