## Context

`AiToolDetectionService.TOOL_GUIDANCE` (lines 29-42 of the file) holds an explicit-allowlist map of display name → `ToolGuidance` (chat panel, paste action, optional prompt prefix, autosave flag). Twelve tools have explicit entries; the remaining sixteen — including the four added in `add-openspec-1-3-tool-support` — fall through to `DEFAULT_GUIDANCE`. Of the four newly-added tools, Junie and Lingma are IDE-resident and don't have a stable, well-documented panel name we can hard-code yet, but ForgeCode and Bob Shell are terminal/CLI-based and fit cleanly into the existing pattern used for Claude Code, Gemini, Codex, and OpenCode.

The change also closes a small spec gap: the existing `ai-integration` requirement "Tool-specific guidance" only has a scenario for IDE panel tools. The terminal-CLI guidance behavior has been live since Claude Code's entry was added but was never spec'd. Adding a sibling scenario captures the existing-and-now-extended behavior in one shot.

## Goals / Non-Goals

**Goals:**
- ForgeCode and Bob Shell get tailored guidance text matching the Codex/OpenCode pattern.
- The `ai-integration` "Tool-specific guidance" requirement gains a CLI-tool scenario, with no change to the existing IDE-panel scenario.
- Tests directly assert both new entries don't fall through to `DEFAULT_GUIDANCE`.

**Non-Goals:**
- Per-tool guidance for Junie/Lingma. Out of scope; revisit when the JetBrains/Alibaba panel names are confirmed in real-world use.
- Refactoring TOOL_GUIDANCE into a fourth parallel registry map or merging all four maps into a `ToolMetadata` record. Tempting, but this stays a registry edit.
- Adding `promptPrefix` decoration to either entry. Bob Shell's upstream adapter writes `opsx-<id>.md` files, but the plugin would need to verify Bob actually accepts slash-prefixed input (i.e. `/opsx-foo` vs plain `opsx-foo`) before committing to a prefix. Defer.

## Decisions

### Decision 1: Mirror the Codex/OpenCode shape

**Choice:**
```
"ForgeCode" → ToolGuidance("terminal", "Paste into ForgeCode", null, true)
"Bob Shell" → ToolGuidance("terminal", "Paste into Bob Shell", null, true)
```

**Rationale:** Both tools are invoked from a shell (ForgeCode is `forge`/`forgecode`, Bob Shell is `bob` or similar). The chat-panel name `"terminal"` is what Codex and OpenCode use, the paste-action follows the existing `"Paste into <Tool>"` template, prompt prefix is null pending a confirmed slash convention, and `canAutoSave: true` matches the other terminal tools' UX (autosave-then-paste flow).

**Alternatives considered:**
- Use chat-panel name `"shell"` instead of `"terminal"`. Rejected: the IntelliJ-side UX surfaces this string in user-visible copy and "terminal" is the noun every other CLI tool entry uses; consistency over precision.
- Add prompt prefix `/opsx-` to Bob Shell because Bob's upstream adapter writes `opsx-<id>.md`. Rejected: the file naming convention is upstream-side; it doesn't tell us whether Bob's CLI parser interprets `/opsx-foo` as a command or as literal text. Without confirmation, prefer a null prefix that won't decorate prompts incorrectly.

### Decision 2: Defer Junie + Lingma per-tool guidance

**Choice:** Both stay on `DEFAULT_GUIDANCE` ("your AI tool" / "Paste into your AI tool").

**Rationale:** Junie is JetBrains-resident; Lingma is an Alibaba-issued IDE plugin. Neither has a publicly-documented, stable chat-panel name we can confidently put in user-facing copy. Guessing wrong (e.g., calling Junie's panel "Junie chat" when it's actually "JetBrains AI Assistant") would surface incorrect instructions to the user. `DEFAULT_GUIDANCE` is generic but accurate.

**Alternatives considered:**
- Add provisional entries: `("Junie chat", "Open Junie chat and paste the prompt", null, false)`. Rejected for the reason above; no known correctness anchor.
- Block the entire change until Junie/Lingma can be verified. Rejected: ForgeCode and Bob Shell are independently deliverable; coupling them slows good polish.

### Decision 3: Spec scenario lives in `ai-integration`, not `plugin-core`

**Choice:** The new "Terminal CLI tool guidance" scenario goes under the existing `ai-integration` "Tool-specific guidance" requirement, not as a new requirement and not in `plugin-core`.

**Rationale:** The existing requirement already covers all guidance behavior generically; we're simply adding a sibling scenario for the CLI case alongside the IDE-panel case. Creating a new requirement would imply a new capability, which there isn't.

**Alternatives considered:**
- Land just the implementation; skip the spec change entirely. Rejected: OpenSpec validation requires at least one delta per change, and the gap is a real one worth closing.
- Promote `TOOL_GUIDANCE` to a spec'd contract enumerating which tools have explicit entries. Rejected: hard-codes UX detail into the spec; better to spec behavior, not registry membership.

## Risks / Trade-offs

- **Risk:** Bob Shell's CLI may want a prompt prefix the plugin isn't supplying. → Mitigation: null prefix is harmless (the prompt body is delivered verbatim); a follow-up change can add the prefix once confirmed.
- **Risk:** Future tool additions in OpenSpec 1.4+ will continue to land without TOOL_GUIDANCE entries unless explicitly added. → Mitigation: not new — same risk has existed since the map was added. The scenarios pattern this change establishes makes future additions easier to spec; structural fix is a future refactor.
- **Trade-off:** No `promptPrefix` for Bob Shell despite the upstream adapter hinting at `opsx-<id>` naming. Easy to revisit; intentional underspec.

## Migration Plan

- No migration. Two map entries + one spec scenario.
- Rollout: ships in the next plugin patch release. No flag.
- Rollback: revert the commit. Both entries are isolated; the spec scenario is additive.
- **Sequencing:** apply this change *after* `add-openspec-1-3-tool-support` lands on `main`. The new entries reference display names ("ForgeCode", "Bob Shell") that only exist in `TOOL_TYPES` after that change merges.

## Open Questions

- Does Bob Shell accept a slash prefix? If yes, a follow-up should add `promptPrefix: "/opsx-"`. Filing as out-of-scope for now per the proposal.
