# OpenSpec IntelliJ Plugin

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/30678-openspec.svg)](https://plugins.jetbrains.com/plugin/30678-openspec)

An IDE-native client for the [OpenSpec](https://github.com/fission-ai/openspec) spec-driven development framework by [Fission AI](https://github.com/fission-ai). Browse specs, orchestrate the propose → generate → implement → archive lifecycle, and route AI-generated artifacts through the tool of your choice — all without leaving IntelliJ.

---

## Four Ways to Use It

| Persona | You are... | AI setup? | Get started |
|---------|-----------|-----------|-------------|
| **Spec Browser** | A reviewer, lead, or PM who wants to browse specs and track coverage | None | [Getting Started](docs/getting-started-browser.md) |
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

**OpenSpec CLI (optional):** The plugin works without the CLI, but the full experience — schema management, CLI-enhanced validation, and agent instruction updates — requires it:

```bash
npm i -g @fission-ai/openspec
```

---

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, coding standards, and PR process.

```bash
./gradlew build      # Compile and run tests
./gradlew runIde     # Launch sandboxed IDE with plugin
./gradlew test       # Tests only
```

---

## Links

- [Feature Reference](docs/feature-reference.md) — Complete reference for all plugin features, settings, and troubleshooting
- [Feature Comparison Matrix](docs/feature-comparison-matrix.md) — How this plugin compares to VS Code alternatives
- [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30678-openspec) — Plugin listing
- [OpenSpec Framework](https://github.com/fission-ai/openspec) — The spec-driven development framework by Fission AI
