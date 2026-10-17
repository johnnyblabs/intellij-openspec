## Context

The plugin's tool knowledge lives in `AiToolDetectionService` as four maps keyed by display name:
- `TOOL_DIRS`: one directory per tool.
- `TOOL_TYPES`: CLI or IDE panel.
- `CLI_TOOL_IDS`: the `--tools` value.
- `TOOL_GUIDANCE`: where to paste.

Detection checks `<dir>/` and `<dir>/{skills,commands,prompts,workflows}`. The setup wizard (`getAllToolNames`), Manage AI Tools and `ScaffoldingService` read these maps. Legacy cleanup parses CLI output and holds no tool paths.

Upstream's registry is `AI_TOOLS` in `dist/core/config.js`:
- **Per tool:** `name`, `value` (the id), `skillsDir`, optional `detectionPaths`, optional `legacySkillsDirs`.
- **Aliases:** `TOOL_ID_ALIASES` maps `windsurf` to `devin`.
- **Detection** (`available-tools.js`): `detectionPaths` if present, otherwise `skillsDir`. On a shared `.agents` root, the `.openspec-target` file names the owning tool (`shared-skill-target.js`).
- **Listing:** no CLI command lists tools. `init --help` prints the ids, line-wrapped.

## Goals / Non-Goals

**Goals:** the plugin's tools equal the CLI's registry, verified by a contract test; detection reports what upstream would; no id is sent to a CLI that rejects it; saved preferences survive renames.

**Non-Goals:**
- Changing delivery modes or guidance text beyond the renamed tools.
- Global (home-directory) tools such as MiniMax Code. They are listed, but project detection can't see a global install, so they show as not detected.

## Decisions

### D1. One record per tool, checked against the captured registry

`record AiTool(String id, String name, List<String> aliases, String skillsDir, String globalSkillsDir, List<String> detectionPaths, List<String> legacySkillsDirs, ToolType type, String sinceCli)`, in a single `AiToolRegistry` list. Guidance is keyed by id. The four maps are removed, and `getAllToolNames` reads the registry.

The display name is upstream's `name`, the label OpenSpec's own tool picker shows (for example "Devin Desktop (formerly Windsurf)"). Its `successLabel` is unsuitable for some tools ("Continue (VS Code / JetBrains / Cli)"). That renames 12 of the plugin's 30 tools. `type` (CLI or IDE panel) is plugin-only, since upstream has no such field. New tools are CLI only where the tool is plainly a terminal agent; otherwise they are IDE panel, which means clipboard delivery, the safe default.

The contract test loads `fixtures/cli/1.14.3/ai-tools.json`, a JSON dump of `AI_TOOLS` and `TOOL_ID_ALIASES` from the installed package (`scripts/capture-ai-tools-fixtures.py`). It asserts that each record's id, name, skillsDir, globalSkillsDir, detectionPaths and legacySkillsDirs equal the dump's, and that the id sets are equal. A second check parses the captured `init-help.txt` id list, so the dump and the CLI's own help can't disagree.

### D2. `sinceCli` from captured registries, not memory

The script dumps `AI_TOOLS` from every published CLI from 1.3.0 (19 versions) and keeps a dump wherever the registry changed (12 files). A tool's `sinceCli` is the first dump containing the id the plugin passes, which the contract test checks. Before offering a tool or passing `--tools`, the plugin filters with `CliVersion.atLeast(detected, sinceCli)`. With no CLI, the full list is shown, because the built-in Propose doesn't pass `--tools`.

Devin Desktop is passed as `windsurf`. The captures show why that works everywhere: `windsurf` is an id from 1.3.0 to 1.6.0, then from 1.7.0 the id is `devin` and `TOOL_ID_ALIASES` maps `windsurf` to it. Real `init --tools windsurf` runs on 1.7.0 and 1.13.2 both write `.devin/`. So its `sinceCli` is 1.3.0.

### D3. Detection is a port of upstream's, contract-tested on captured layouts

`AiToolDetectionService.detect()` ports `getAvailableTools` (`available-tools.js`) and the shared-root reconciliation it calls (`shared-skill-target.js`):
1. **Candidates.** A tool with a global skills directory is a candidate when any OpenSpec skill exists under `~/<dir>/skills/`. A tool with `detectionPaths` is a candidate when any path exists, as a file or a directory. Otherwise a tool is a candidate when its `skillsDir` is a directory.
2. **Shared roots.** Candidates sharing a `skillsDir` (`.agents` has Amp, Antigravity, Codex, GSD, Zed and Universal) are reconciled to one owner. In order: the tool named in `<root>/skills/.openspec-target`; Codex when the root's generated skills hold Codex-only syntax (`$openspec-`); Codex when the skills are generic (`/openspec-`) but Codex has skills under its old `.codex` root; the tool inferred from that syntax; Universal when the root already has OpenSpec skills; a tool with skills under an old root; then Universal.
3. **Kept tools.** A candidate is kept when it is global, owns its root after reconciliation, or has an independent signal: an existing detection path that doesn't end in `/skills`. That's why Antigravity (`.agents/workflows`) survives next to a Codex-owned root, and why a bare `.github` detects nothing.

The contract fixtures are 30 projects built by real `init --tools` runs (some with CLI 1.6, for the directories tools used before) and edge cases. Each records the tools upstream's own `getAvailableTools` reports. The test rebuilds each layout on disk and asserts the plugin detects the same ids. Generated `SKILL.md` content is kept only for the lines naming a skill, which is what the inference reads.

"Configured" status keeps today's meaning: a detected tool with OpenSpec skills (`skills/openspec-*`) or command files under its current, old or global directory.

### D4. Renames keep working

A record carries `aliases` (Windsurf, Roo Code, Bob Shell, Kimi CLI). A saved `preferredTool` that matches an alias resolves to the record, and is rewritten to the current name the next time Settings are applied. The `--tools` value for Devin Desktop stays `windsurf`, which every supported CLI accepts and which 1.14 maps to `devin`.

## Risks / Trade-offs

- **The registry changes every few releases.** → The contract test fails on a re-capture with a new CLI, which is the intended prompt to update the records.
- **The port could drift from upstream's detection.** → The detection cases are contract fixtures. A recapture with a new CLI that changes a verdict fails the test.
- **Detection may be slower,** checking more paths. → It is still a handful of `exists` checks, done off the UI thread as today.

## Migration Plan

Code and docs only. Saved preferences migrate through aliases. To roll back, revert the commit.
