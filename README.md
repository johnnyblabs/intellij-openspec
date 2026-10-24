# OpenSpec IntelliJ Plugin

[![Build](https://github.com/johnnyblabs/intellij-openspec/actions/workflows/build.yml/badge.svg)](https://github.com/johnnyblabs/intellij-openspec/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/30678-openspec.svg)](https://plugins.jetbrains.com/plugin/30678-openspec)

<!-- Plugin description -->
An IDE-native client for the [OpenSpec](https://github.com/fission-ai/openspec) spec-driven development framework by [Fission AI](https://github.com/fission-ai). Browse specs, orchestrate the propose → generate → implement → archive lifecycle, and route AI-generated artifacts through the tool of your choice — all without leaving your IDE.

**Features:**

- **Changes browser & Change Deltas pane** — a master/detail Changes tree beside a pane that shows what the selected change will add, modify or remove in your specs, for the change or any of its artifacts, with content-aware spec search through Search Everywhere (over requirement and scenario text), artifact-status badges on tree icons (a change whose artifacts are complete and whose tasks are all done gets a check mark and an "all tasks done" cue, so finished work stands out)
- **Validation you can navigate** — real-time editor inspections and quick-fixes matching the OpenSpec CLI's own parser, plus a Validate action (from the toolbar or the Project View context menu, scoped to the clicked change or spec) whose results render grouped by file with clickable file:line links and per-severity coloring
- **Change lifecycle** — create, implement, verify, and archive OpenSpec changes from a dedicated tool window, with schema-aware completeness gates and task-level progress. With OpenSpec CLI 1.5 or later, archiving runs through your CLI's own rules, and a refusal shows the CLI's reason and suggested fix
- **Works with your AI tooling** — detects the 50 AI coding tools OpenSpec supports (Claude Code, GitHub Copilot, Cursor, Gemini CLI, and more), the way the OpenSpec CLI detects them, and hands generation off to the one you already use, or calls the Anthropic, OpenAI, or Google APIs directly with your own key
- **Schema authoring** — fork, create, and validate custom workflow schemas, with template editing and resolution-provenance tags straight from the CLI
- **Stores & worksets** — browse and manage the OpenSpec CLI 1.5 coordination model, with health diagnostics and graceful support back to the 1.4 workspace model
- **Setup wizard** — guided onboarding for new and existing projects; the plugin works with or without the OpenSpec CLI installed and degrades gracefully

Compatible with IntelliJ IDEA, PyCharm, GoLand, WebStorm, RubyMine, and the rest of the JetBrains IDE family, 2024.2 and later. Spec browsing and validation are fully language-agnostic.
<!-- Plugin description end -->

> **Maintenance: Living** — updated as part of every relevant change. See the [documentation index](docs/README.md) for the full doc map and each doc's maintenance class.

---

## Four Ways to Use It

| Persona | You are... | AI setup? | Get started |
|---------|-----------|-----------|-------------|
| **Spec Browser** | A reviewer, lead, or PM who wants to browse specs | None | [Getting Started](docs/getting-started-browser.md) |
| **IDE-First Developer** | A dev using Copilot, Cursor, Devin Desktop (formerly Windsurf), or Cline inside IntelliJ | None (uses your existing tool) | [Getting Started](docs/getting-started-copilot.md) |
| **CLI Companion** | A dev using Claude Code, Gemini CLI, or another terminal AI | None (uses your existing tool) | [Getting Started](docs/getting-started-cli-companion.md) |
| **Standalone API User** | A dev with an API key who wants a fully self-contained workflow | API key only | [Getting Started](docs/getting-started-api.md) |

---

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30678-openspec) (search for **OpenSpec**), or build from source:

```bash
git clone https://github.com/johnnyblabs/intellij-openspec.git
cd intellij-openspec
./gradlew build
# Install from: build/distributions/OpenSpec-*.zip
```

**Requirements:** IntelliJ IDEA 2024.2+ (Community or Ultimate), Java 21 JDK for building from source.

**OpenSpec CLI (optional):** The plugin works without the CLI via its built-in fallback paths, and AI-tool detection (all 50 tools OpenSpec supports) is plugin-side — it works on any CLI, or none. **Minimum supported CLI is 1.3.0** (see the canonical [Version support](docs/openspec-support.md#version-support) block for the full per-version support list). The plugin tracks the CLI's evolving model per version: on **1.4.x** it surfaces the legacy coordination layer (workspaces, context stores, and initiatives, plus the `workspace-planning` schema) that exists in the `[1.4.0, 1.5.0)` window; on **1.5.0+** it surfaces the current **store/workset** model that replaced it. On **1.6.x**, **1.7.x**, **1.8.x**, **1.9.x**, **1.10.x**, **1.11.x**, and **1.12.x** that coordination model is unchanged; **1.7.x**, **1.8.x**, **1.9.x**, **1.10.x**, **1.11.x**, and **1.12.x** support is additive — the plugin recognizes the CLI's new optional change-metadata (`skip_specs`, plus 1.8's `retire_capabilities`) and config fields (`operations`, `defaultStore`, plus 1.8's `githubCopilot`) and the additive `status --json` fields, all tolerated without new gates. **1.8** also relaxed one built-in check to stay in step with the CLI: when the CLI isn't installed, a requirement that has a body but no `SHALL`/`MUST` is a non-failing warning in default validation (strict still fails it), matching OpenSpec 1.8's default. **1.9** is a strict additive superset of 1.8 — its default and strict validation verdicts are byte-identical, so 1.9 support needed no built-in change. **1.10** is likewise a strict additive superset of 1.9 — its validation engine is byte-identical in source and its verdicts are unchanged, so 1.10 support also needed no built-in change (1.10 is the first two-digit minor, so the plugin's numeric version comparison is tested to order 1.10.0 above 1.9.0). **1.11** is an additive, safe-direction superset of 1.10 — its verdicts over the parity corpus are byte-identical to 1.8, and its one new validation rule (a spec whose `## Purpose` is still a placeholder is now a warning, a failure under strict) only makes the CLI stricter, so 1.11 support also needed no built-in change and the new rule is locked by a positive-control fixture. **1.12** is the same shape — an additive, safe-direction superset of 1.11 with unchanged verdicts; its one new diagnostic (an informational "archive would refuse this delta" note when a change's delta targets a not-yet-existing spec) never affects the verdict and is already surfaced at the correct severity, so 1.12 support also needed no built-in change. **1.13** keeps the verdicts unchanged but made the CLI more permissive in two places the plugin now mirrors. `RENAMED`/`REMOVED` delta entries may use any bullet (`-`, `*`, `+`), and the built-in validator, the editor inspection and spec sync all accept them. Task progress counts every task style the CLI counts (`*`, `+`, `1.`, `[ x ]`, …), so the plugin's numbers match the CLI's. A rejected item id also no longer reads as a passing validation. **1.14** reworded the legacy-cleanup notice that `openspec update` prints. The plugin recognizes both wordings, and since 1.14 warns to back up custom content first, its cleanup review starts with no files ticked. Its new output fields are ignored, and its new warnings (an unrecognized `.openspec.yaml` key, a very long requirement, text that archiving would drop) only fail under strict, so the built-in fallback leaves them to the CLI. **1.15** changes nothing the plugin reads: its validation verdicts and output match 1.14, and its new standalone binary (installable without Node.js) reports its version the same way as the npm install. Note that **OpenSpec CLI 1.5.0 replaced the coordination commands and the `workspace-planning` schema with a store/workset model**; on 1.5.0+ the plugin stops calling the removed commands and instead shows the new model in the Coordination tab — your registered **stores** (with id, root, and health) and your local **worksets** (with their member folders), sourced from the CLI with a built-in fallback that reads OpenSpec's global data directory. Any surviving pre-1.5 state appears in a muted, read-only "Legacy (pre-1.5)" group. With CLI 1.5.0+ the IDE also offers store/workset **write actions** — creating or registering a store, revealing a workset's member folders, and creating or removing stores/worksets (store Remove is a guarded, destructive delete) — all delegating to the CLI; the plugin never migrates state — it only reflects what the CLI owns. The surface is language-agnostic and works across the JetBrains IDE family. Users on CLI 1.0, 1.1, or 1.2 will see a one-time startup notification recommending upgrade — the plugin continues to function but features that require the CLI are disabled:

```bash
npm i -g @fission-ai/openspec
```

**Windows note:** npm installs the launcher as `openspec.cmd` (not bare `openspec`). The plugin auto-detects it from `%APPDATA%\npm\` and `%LOCALAPPDATA%\npm\`, plus winget shims. If auto-detect misses your install, set the manual path in **Settings → Tools → OpenSpec → CLI Path** to the full `.cmd` location, e.g. `C:\Users\<you>\AppData\Roaming\npm\openspec.cmd`.

---

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, coding standards, and PR process.

```bash
./gradlew build      # Compile and run tests
./gradlew runIde     # Launch sandboxed IDE with plugin
./gradlew test       # Tests only
```

CI verifies the plugin on **Windows and macOS in addition to Linux**, so platform-specific behavior — Windows data-dir resolution, backslash/UNC path handling, the `.cmd` launcher shim, and root canonicalization across symlinks and short paths — is exercised on a real host of each OS.

---

## Links

- [Documentation Index](docs/README.md) — Map of every doc: purpose, audience, and maintenance class
- [Feature Reference](docs/feature-reference.md) — Complete reference for all plugin features, settings, and troubleshooting
- [OpenSpec Client Coverage](docs/openspec-support.md) — What the plugin supports vs. the OpenSpec client, by CLI version
- [Feature Comparison Matrix](docs/feature-comparison-matrix.md) — How this plugin compares to VS Code alternatives
- [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30678-openspec) — Plugin listing
- [OpenSpec Framework](https://github.com/fission-ai/openspec) — The spec-driven development framework by Fission AI
