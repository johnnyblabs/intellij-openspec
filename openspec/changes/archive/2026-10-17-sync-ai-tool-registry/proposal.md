## Why

The plugin detects AI coding tools and passes their ids to `openspec init --tools` and `update`. Its list was written for OpenSpec CLI 1.4 and has drifted from the registry the CLI actually uses (`AI_TOOLS` in `dist/core/config.js`). An openspec-guru comparison against 1.14.3, with real `init --tools` runs, found:
- **20 tools missing.** OpenSpec 1.14.3 supports 50 tools and the plugin knows 30. The 20 were added across CLI 1.6–1.14: Amp, Zed, Warp, Grok and others.
- **Four tools looked for in the wrong place.** OpenSpec now writes Windsurf (rebranded Devin Desktop) to `.devin/`, Codex to `.agents/`, Antigravity to `.agents/` and Kimi (now Kimi Code) to `.kimi-code/`. The plugin looks for the old directories, so after `openspec init` these never show as set up.
- **A false positive:** GitHub Copilot counts as detected whenever a `.github/` folder exists, which is nearly every GitHub repository. Upstream checks specific paths.
- **Outdated names:** 12 tools have new names in OpenSpec's tool picker. Among them, Windsurf, Roo Code, Bob Shell and Kimi CLI are now Devin Desktop (formerly Windsurf), Zoo Code, IBM Bob and Kimi Code, and Gemini is Gemini CLI.

These facts are spread over four parallel maps keyed by display name (`TOOL_DIRS`, `TOOL_TYPES`, `CLI_TOOL_IDS`, `TOOL_GUIDANCE`), with no test against the real registry.

Tracked in the linked tracker entry.

## What Changes

- **One registry record per tool,** mirroring upstream's `AI_TOOLS` fields: id, display name, skills directory, detection paths, older directories, CLI or IDE-panel type, guidance, and the first CLI version that accepts the id. It is contract-tested against the registry captured from the real CLI.
- **Detection follows upstream's rule:** a tool's detection paths when it has them, otherwise its skills directory. Older directories count too, so existing projects stay detected. On the shared `.agents` directory, ownership is decided as upstream decides it: the `.openspec-target` marker, then the syntax inside the generated skills. Tools that install into the home directory are detected there. Copilot is detected only from its specific paths.
- **All 50 tools are listed,** and the plugin offers or passes a tool id only when the detected CLI accepts it. A 1.3 CLI isn't sent an id added in 1.14. `windsurf` is still sent for Devin Desktop, because every CLI accepts it and 1.14 maps it to `devin`.
- **Current display names** as OpenSpec's tool picker shows them, with the 12 old names kept as aliases so a saved preferred tool still matches.
- **Docs:** README and the Marketplace Getting Started copy say the plugin detects the 50 tools OpenSpec 1.14 supports, and the feature reference's tool table matches the registry.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `plugin-core`: AI tool detection mirrors the CLI's registry (50 tools at 1.14.3) through one record per tool, follows upstream's detection rule and current directories, and gates tool ids by the detected CLI version.

## Impact

- **Code:**
  - `AiToolDetectionService`: one registry replacing four maps, the detection rule, and version gating.
  - The setup wizard and Manage AI Tools dialog, which read the list.
  - `ScaffoldingService` and `ManageAiToolsDialog` `--tools` invocations.
  - Settings: preferred-tool aliases.
- **Tests/fixtures:**
  - `fixtures/cli/<version>/ai-tools.json`: the registry dumped from every published CLI from 1.3.0, kept where it changed, so each tool's first version is captured;
  - `init-help.txt`;
  - 30 detection cases: real `init --tools` layouts (some from CLI 1.6 for the old directories) and edge cases, each with the tools upstream's own detection reports;
  - a contract test that the plugin registry equals the captured one;
  - detection tests including the Copilot false positive and `.openspec-target`.
- **Docs:** README, `docs/marketplace/getting-started.html` (re-paste needed), `docs/feature-reference.md`, and the CHANGELOG.
