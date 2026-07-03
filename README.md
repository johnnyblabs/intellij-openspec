# OpenSpec IntelliJ Plugin

[![Build](https://github.com/johnnyblabs/intellij-openspec/actions/workflows/build.yml/badge.svg)](https://github.com/johnnyblabs/intellij-openspec/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/30678-openspec.svg)](https://plugins.jetbrains.com/plugin/30678-openspec)

An IDE-native client for the [OpenSpec](https://github.com/fission-ai/openspec) spec-driven development framework by [Fission AI](https://github.com/fission-ai). Browse specs, orchestrate the propose → generate → implement → archive lifecycle, and route AI-generated artifacts through the tool of your choice — all without leaving IntelliJ.

---

## Four Ways to Use It

| Persona | You are... | AI setup? | Get started |
|---------|-----------|-----------|-------------|
| **Spec Browser** | A reviewer, lead, or PM who wants to browse specs | None | [Getting Started](docs/getting-started-browser.md) |
| **IDE-First Developer** | A dev using Copilot, Cursor, Windsurf, or Cline inside IntelliJ | None (uses your existing tool) | [Getting Started](docs/getting-started-copilot.md) |
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

**OpenSpec CLI (optional):** The plugin works without the CLI via its built-in fallback paths. **Minimum supported CLI is 1.3.0**; **recommended CLI is 1.4.x** for full feature parity (detection of all 30 supported AI tools, the `workspace-planning` workflow schema, and the coordination layer — workspaces, context stores, and initiatives, which exist in the `[1.4.0, 1.5.0)` window). Note that **OpenSpec CLI 1.5.0 replaced the coordination commands and the `workspace-planning` schema with a store/workset model**; on 1.5.0+ the plugin stops calling the removed commands and instead shows the new model in the Coordination tab — your registered **stores** (with id, root, and health) and your local **worksets** (with their member folders), sourced from the CLI with a built-in fallback that reads OpenSpec's global data directory. Any surviving pre-1.5 state appears in a muted, read-only "Legacy (pre-1.5)" group. With CLI 1.5.0+ the IDE also offers store/workset **write actions** — creating or registering a store, revealing a workset's member folders, and creating or removing stores/worksets (store Remove is a guarded, destructive delete) — all delegating to the CLI; the plugin never migrates state — it only reflects what the CLI owns. The surface is language-agnostic and works across the JetBrains IDE family. Users on CLI 1.0, 1.1, or 1.2 will see a one-time startup notification recommending upgrade — the plugin continues to function but features that require the CLI are disabled:

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

- [Feature Reference](docs/feature-reference.md) — Complete reference for all plugin features, settings, and troubleshooting
- [OpenSpec Client Coverage](docs/openspec-support.md) — What the plugin supports vs. the OpenSpec client, by CLI version
- [Feature Comparison Matrix](docs/feature-comparison-matrix.md) — How this plugin compares to VS Code alternatives
- [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30678-openspec) — Plugin listing
- [OpenSpec Framework](https://github.com/fission-ai/openspec) — The spec-driven development framework by Fission AI
