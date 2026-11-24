# Proposal

## Why

The plugin's tool list matches OpenSpec CLI 1.17: 50 tools, Amazon Q included. CLI 2.0 retired Amazon Q, and CLI 2.1 added 38 tools, for 87 in all. With a 2.x CLI, the plugin still offers Amazon Q, which `init` refuses, and it can't offer, detect or tick any of the new tools. Manage AI Tools also says OpenSpec "wrote its /opsx commands", while 2.x writes skills only. It is tracked in the linked tracker entry for the 2.x tool registry.

## What Changes

- **One tool list for every release.** Each tool records the first CLI that accepts it and, for Amazon Q, the release that retired it (2.0.0). The tools a release accepts, in the plugin's order, are exactly that release's registry, in its order, from 1.3.0 to 2.1.1. MiniMax Code's install location follows the release: the home folder up to 2.0, the project's `.minimax` from 2.1.
- **The 38 tools new in CLI 2.1** are added with upstream's names, folders and detection paths, offered and passed to `init` only on CLI 2.1.0 or later. Each gets the plugin's CLI or IDE type from its own documentation; a tool without clear evidence is left unclassified, with no badge and the generic paste guidance.
- **Amazon Q on CLI 2.x** is not offered or passed to `init`, which refuses it. The plugin adds no Amazon Q row of its own: the CLI's `openspec update` removes OpenSpec's generated Amazon Q files and says to set up Kiro, and stops reporting once only edited files remain. On 1.x nothing changes.
- **2.x wording.** Manage AI Tools' subtitle and the "set up" notice say OpenSpec wrote the tool's skills on CLI 2.x, and its `/opsx` commands on 1.x. When the CLI's registry has a setup note for the tool (Hermes from CLI 1.7, and on 2.1 nine existing tools that use the universal skills folder), the notice includes that release's note, under the same rule the CLI prints it by.
- **A tool reached only through the shared folder** says so: on a 2.x project, a set-up tool whose skills are only the shared `.agents/skills/` copy, and whose own folder isn't `.agents`, gets a secondary line, "via shared .agents skills" (upstream's own label for that folder).
- **Regional variants sharing a folder** (Trae and Trae CN on `.trae`, Qoder and Qoder CN on `.qoder`, Zencoder and Zenflow on `.zencoder`) count as set up as CLI 2.1 counts them: when the tool list names neither, only the first.
- **Order stays the CLI's.** Manage AI Tools keeps listing configured tools, then detected, then the rest in the CLI's registry order, which is how the CLI's own picker orders them. The new tools follow the existing ones, as upstream lists them.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: the tool records gain a retiring release; the registry contract covers every captured release; new requirements for retired tools, tools added in 2.1, the shared-folder line and regional variants.
- `ai-integration`: the set-up notice names skills or commands by CLI line and carries the CLI's setup note; guidance for unclassified tools.

## Impact

- **Production code:** `AiToolRegistry` (the 38 tools, `untilCli`, setup notes, `acceptedBy`), `SkillLinkDirs` (the 2.1 link folders), `AiToolDetectionService` (`ToolType.UNCLASSIFIED`, the shared-folder flag, the regional-variant rule), `ManageAiToolsDialog` (subtitle, notice, the shared-folder line, the tool scan off the UI thread). MiniMax Code's per-release folders already come from `DetectionRules`.
- **Tests and captures:** the registry contract runs over every captured release with no known gaps left (the `tool-registry-2x` entries in `KnownGaps` are removed); `acceptedBy(amazon-q, "2.0.0")` is false, backed by the captured `init` error; MiniMax Code's location per release; the setup notes against every captured registry that has them (1.7.0 on); the invocation and link-folder contracts stop skipping tools the registry didn't know; captures for the regional variants. The marker table from the skills-scope change then ticks every machine-detected tool.
- **Docs:** `docs/feature-reference.md`, `README.md`, `docs/marketplace/getting-started.html` and `docs/feature-comparison-matrix.md`, which all state the tool count; `CHANGELOG.md`.
- **Corrections to the tracker entry:** a permanent disabled Amazon Q row would contradict upstream, which deliberately stops reporting a retired tool once only edited files remain, so there is none. Upstream's picker isn't alphabetical, so the order is kept as the CLI's. `ToolGuidance.promptPrefix` is read by no production code, so there is no `/opsx` prefix to version there; and since 2.x writes no command files and the plugin's handoff already follows the files on disk, command paths need no version gate.
- **Compatibility:** IntelliJ 2024.2+ unaffected; no new platform APIs. CLI 1.3.0 to 1.17 see the same tools as today.
- **Out of scope:** removing 1.x command files during `update` (the update-migration change), and the CLI's own cleanup of Amazon Q files, which `openspec update` does on 2.x.
