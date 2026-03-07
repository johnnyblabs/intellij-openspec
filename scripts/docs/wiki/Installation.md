# Installation

## Prerequisites

| Requirement | Version |
|------------|---------|
| **Java JDK** | 17 or later |
| **IntelliJ IDEA** | 2024.1+ (Community or Ultimate) |
| **OpenSpec CLI** | Optional — `npm i -g openspec-dev` |

## Build from Source

```bash
git clone <repo-url>
cd OpenSpecPlugin
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

The CLI extends plugin capabilities with additional commands and validation. Install it via npm:

```bash
npm install -g openspec-dev
```

### CLI Detection Cascade

The plugin locates the CLI automatically using this order:

1. **Settings path** — manually configured in Settings → Tools → OpenSpec
2. **Bare command** — `openspec` on the system PATH
3. **Login shell** — spawns a login shell to resolve PATH (handles macOS `.zprofile` issues)
4. **Common paths** — checks `/usr/local/bin/openspec`, `~/.npm-global/bin/openspec`, etc.

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
