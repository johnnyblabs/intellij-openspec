## Why

OpenSpec CLI 1.3.0 added four new AI tool integrations — Junie (JetBrains), Lingma, ForgeCode, and IBM Bob — bringing the supported-tool count from 24 to 28. The plugin's `AiToolDetectionService` is currently hardcoded to the 1.2.x set of 24 tools across three parallel maps, so users who initialize a project with any of the four new tools see no detection in the Setup Wizard, status bar, or AI bridge — even though their `.junie/`, `.lingma/`, `.forgecode/`, or `.bob/` directories exist on disk.

## What Changes

- Add four entries each to `TOOL_DIRS`, `TOOL_TYPES`, and `CLI_TOOL_IDS` in `AiToolDetectionService`. IDs verified against the OpenSpec 1.3.1 adapter source (`@fission-ai/openspec/dist/core/config.js`):

  | Display name | Directory | CLI ID | Tool type |
  |---|---|---|---|
  | Junie | `.junie` | `junie` | IDE_PANEL (JetBrains AI) |
  | Lingma | `.lingma` | `lingma` | IDE_PANEL (Alibaba IDE plugin) |
  | ForgeCode | `.forge` | `forgecode` | CLI |
  | Bob Shell | `.bob` | `bob` | CLI |

- **Note the directory mismatch for ForgeCode**: the upstream `value` is `forgecode` but the directory it creates is `.forge`. The plugin's `TOOL_DIRS` keys on directory, so the entry is `.forge → ForgeCode`.
- Update the `plugin-core` "AI tool detection" requirement from "24 AI tools" to "28 AI tools".
- Add a unit test assertion for each new tool in `AiToolDetectionServiceTest` (one detected-tool fixture per directory + CLI-ID round-trip).
- Update README "Setup" section to reflect the broader tool support (28 tools backed by OpenSpec 1.3.x).
- No CLI-version gating. Detection is filesystem-based; the directory only exists if a 1.3.x CLI created it, so version compatibility is implicit. (See **Impact / Out of Scope** below for the rationale and the future condition that would warrant gating.)

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: the "AI tool detection" requirement updates from "24 tools" to "28 tools" and the scenario list expands to cover the four new tool IDs.

## Impact

- Code: `src/main/java/com/johnnyblabs/openspec/services/AiToolDetectionService.java` — ~12 lines added across the three parallel maps.
- Tests: `src/test/java/com/johnnyblabs/openspec/services/AiToolDetectionServiceTest.java` — four detection assertions added (one per new tool).
- Spec: `openspec/specs/plugin-core/spec.md` — single requirement text bump, exposed via the `add-openspec-1-3-tool-support` delta spec.
- No public API changes, no dependency bumps, no migration. Plugin remains compatible with both OpenSpec CLI 1.2.x (the four new tools simply never appear) and 1.3.x users (they appear when present).
- Risk: low. Additions are purely registry entries; existing 24-tool behavior is untouched.

### Out of scope

- **CLI-version gating per tool.** The plugin does not need to know which CLI version added which tool — the directory's presence on disk is the source of truth. A `minCliVersion` field per tool would only matter if a future feature offered the full 28-tool registry as a *picker* (rather than scanning for already-present directories). No such picker exists today; defer until one is proposed.
- **Bumping the documented minimum CLI version** in README / Settings. The plugin still works against 1.2.x; the four new tools just remain invisible there. No reason to force a hard upgrade.

## References

- Forgejo: johnb/intellij-openspec#193
- Plane: openspec/issue/212 (`26c80801-6f9a-4cad-9828-9fb218f311e3`)
- Upstream releases: [v1.3.0](https://github.com/Fission-AI/OpenSpec/releases/tag/v1.3.0), [v1.3.1](https://github.com/Fission-AI/OpenSpec/releases/tag/v1.3.1)
