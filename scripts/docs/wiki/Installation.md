# Installation

## Prerequisites

| Requirement | Version |
|------------|---------|
| **Java JDK** | 21 or later |
| **IntelliJ IDEA** | 2024.2+ (Community or Ultimate) |
| **OpenSpec CLI** | Optional — standalone installer, npm or Homebrew (see below) |

## Build from Source

```bash
git clone <repo-url>
cd intellij-openspec
./gradlew build
```

The plugin ZIP is produced at `build/distributions/OpenSpec-0.1.0.zip`.

### Install the ZIP

1. Open IntelliJ IDEA → **Settings → Plugins → ⚙ → Install Plugin from Disk...**
2. Select the ZIP file
3. Restart the IDE

## Run in Development Mode

```bash
./gradlew runIde
```

This launches a sandboxed IntelliJ instance with the plugin pre-installed.

## OpenSpec CLI (Optional)

The CLI extends plugin capabilities with additional commands and validation. Install it with any of OpenSpec's [install routes](https://openspec.dev/docs/installation):

```bash
# Standalone installer (no Node.js needed), macOS and Linux:
curl -fsSL https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.sh | sh
# Windows (PowerShell):
irm https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.ps1 | iex
# Or with a package manager:
npm install -g @fission-ai/openspec@latest   # needs Node.js 20.19+
brew install openspec                        # macOS and Linux
```

**Keep one copy of the CLI.** If you install it a second way, for example the standalone installer after an earlier npm install, your terminal runs whichever comes first in PATH, while the plugin uses the first copy it finds. Upgrade the copy you have the way you installed it: re-run the installer (or `openspec install`), `npm install -g @fission-ai/openspec@latest`, or `brew upgrade openspec`. To make the plugin use a specific copy, set **Settings → Tools → OpenSpec → CLI Path**. The plugin's **Update** action runs `openspec update`, which refreshes your project's instruction files; it doesn't upgrade the CLI.

### CLI Detection Cascade

The plugin locates the CLI automatically using this order:

1. **Settings path** — manually configured in Settings → Tools → OpenSpec
2. **Bare command** — `openspec` on the system PATH
3. **Login shell** — spawns a login shell to resolve PATH (handles macOS `.zprofile` issues)
4. **Common paths**, checked only where a file exists:
   - on macOS and Linux: `/opt/homebrew/bin`, `/usr/local/bin`, `/usr/bin`, `/home/linuxbrew/.linuxbrew/bin`, then `$OPENSPEC_INSTALL_DIR` when the IDE's environment sets it, then `~/.local/bin`;
   - on Windows: the npm and winget shims under `%APPDATA%` and `%LOCALAPPDATA%`, then `%OPENSPEC_INSTALL_DIR%`, then `%LOCALAPPDATA%\Programs\openspec\openspec.exe`.

If no CLI is found, the plugin runs in **built-in mode** — core features like init, propose, validate, and archive work without the CLI.

## Verify Installation

After installing the plugin:

1. Open or create a project
2. Look for the **OpenSpec** menu in the top menu bar
3. Look for the **OpenSpec** tool window on the right sidebar
4. Run **OpenSpec → Init** to initialize a project

The status bar at the bottom of the tool window shows CLI version and detected AI tools.

---

**Next:** [[Getting-Started]]
