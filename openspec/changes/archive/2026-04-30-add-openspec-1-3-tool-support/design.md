## Context

`AiToolDetectionService` keeps three parallel maps describing the AI tools the plugin recognizes:

- `TOOL_DIRS` — directory key (`.claude`) → display name (`"Claude Code"`)
- `TOOL_TYPES` — display name → `ToolType` (`CLI` or `IDE_PANEL`)
- `CLI_TOOL_IDS` — display name → CLI registry value used by `openspec init --tools <id>`

The current 24 entries match the OpenSpec CLI 1.2.x adapter set. OpenSpec 1.3.0 added four more — Junie, Lingma, ForgeCode, Bob Shell — bringing the upstream registry to 28. Adding them is a registry edit, not an architectural change. This document mostly documents the small decisions that came up during the registry survey so future readers don't have to re-derive them.

## Goals / Non-Goals

**Goals:**
- Plugin lists all 28 tools from OpenSpec 1.3.x via existing filesystem detection.
- Plugin's `CLI_TOOL_IDS` mapping matches the upstream `value` strings exactly so that `openspec init --tools <id>` calls succeed when the plugin issues them.
- Tests assert detection for each new tool with no fixture drift.

**Non-Goals:**
- Generalizing the three-map structure into a single `ToolMetadata` record. Tempting, but out of scope; touch only what 1.3.x requires.
- CLI-version gating per tool (covered in proposal "Out of scope").
- Documenting a hard minimum CLI version. Plugin keeps soft compatibility with 1.2.x.

## Decisions

### Decision 1: Trust the upstream `config.js` registry as the source of truth

**Choice:** Pin the four new tools' display names, directory keys, and CLI IDs from `@fission-ai/openspec/dist/core/config.js` (the shipped registry), not from individual adapter source files.

```
{ name: 'Junie',     value: 'junie',     skillsDir: '.junie' }
{ name: 'Lingma',    value: 'lingma',    skillsDir: '.lingma' }
{ name: 'ForgeCode', value: 'forgecode', skillsDir: '.forge' }   ← directory ≠ value
{ name: 'Bob Shell', value: 'bob',       skillsDir: '.bob' }
```

**Alternatives considered:**
- Reading each adapter file (`adapters/junie.js`, etc.) to infer paths. Equivalent for now but more brittle — adapter files describe one-off command outputs, while `config.js` is the canonical registry the CLI itself uses for `--tools` validation.
- Hardcoding from the GitHub release notes. Rejected: release notes don't enumerate `skillsDir` precisely.

### Decision 2: ForgeCode's directory is `.forge`, not `.forgecode`

**Choice:** `TOOL_DIRS` entry `.forge → "ForgeCode"`. Documented prominently because the upstream `value: 'forgecode'` doesn't match the directory.

This is the single non-obvious gotcha in the four additions. Any future contributor adding tools should note that for ForgeCode the CLI ID and the directory diverge, and `TOOL_DIRS` keys on directory while `CLI_TOOL_IDS` keys on display name.

### Decision 3: Tool-type classifications

**Choice:**
- **Junie → IDE_PANEL.** JetBrains' AI assistant is integrated as an IDE panel, not a CLI. Matches existing JetBrains-style entries.
- **Lingma → IDE_PANEL.** Alibaba's IDE plugin runs inside the IDE; same pattern.
- **ForgeCode → CLI.** Standalone CLI tool per the project description; matches existing CLI-style entries (Claude Code, Gemini, Codex, OpenCode).
- **Bob Shell → CLI.** Shell-based tool by name and use; CLI is the natural classification.

**Alternatives considered:**
- Defaulting all four to IDE_PANEL (the more common type today). Rejected because misclassifying a CLI tool affects how the AI bridge surfaces commands and would silently degrade UX for ForgeCode/Bob users.
- Requiring explicit upstream metadata for the type (OpenSpec doesn't expose this distinction). Out of scope; this stays a plugin-side editorial call.

### Decision 4: Display name "Bob Shell" verbatim

**Choice:** Use the upstream display string `"Bob Shell"` rather than truncating to `"Bob"`.

The plugin already faithfully shortens some upstream names (`"Auggie (Augment CLI)"` → `"Augment"`) where the upstream label is verbose. "Bob Shell" is short and disambiguating; truncating to "Bob" would invite confusion with unrelated tools (and the upstream `value` is `bob`, which we still use as the CLI ID).

## Risks / Trade-offs

- **Risk:** Future OpenSpec releases add more tools, drift accumulates, registry gets stale again. → Mitigation: tasks include a comment in `AiToolDetectionService` pointing to the upstream `config.js` location so the next contributor knows where to cross-check.
- **Risk:** Plugin running against OpenSpec CLI 1.2.x detects nothing for the four new directories. That's fine — the directories cannot exist on a 1.2.x install — but a user with a 1.3.x project opened on a machine with a 1.2.x CLI will see the directories *listed* by the plugin yet fail to interact with them via the CLI. → Mitigation: not gated, accepted as a transparent CLI-level error per the proposal. The Settings panel already shows the detected CLI version; users have the diagnostic they need.
- **Trade-off:** Three parallel maps stay parallel. A future refactor to a single `ToolMetadata` record would eliminate the chance of a name typo causing a `null` lookup, but the change is small enough that the risk is contained — defer the refactor.

## Migration Plan

- No migration. Registry-only edit.
- Rollout: ships in the next plugin patch/minor release. No flag needed.
- Rollback: revert the commit. The four new entries are isolated; reverting won't affect the existing 24.

## Open Questions

- None blocking. If a future user reports that `.forge` collides with their build tooling (e.g. some other tool puts `.forge` at the project root), we may need to disambiguate — but no such report exists today and OpenSpec's choice of `.forge` is what the CLI emits.
