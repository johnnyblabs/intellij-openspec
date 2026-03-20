# Scripts

Utility scripts for development, testing, and marketplace publishing.

## For Contributors

These scripts work out of the box — no special infrastructure required:

| Script | Purpose |
|--------|---------|
| `setup-tokens.sh` | Interactive guide for configuring API tokens (Forgejo, Plane) |
| `setup-signing.sh` | Generate plugin signing keys for JetBrains Marketplace |
| `capture-screenshots.sh` | Capture marketplace screenshots at 1280x800 |
| `demo-project.sh` | Create a demo project for screenshots and testing |

## Libraries

| File | Purpose |
|------|---------|
| `lib/common.sh` | Shared utilities (logging, environment loading) |

## Build & Test

You don't need any of these scripts to build or test the plugin. Standard Gradle commands work:

```bash
./gradlew build      # Compile + test
./gradlew test       # Tests only
./gradlew runIde     # Launch sandboxed IDE with plugin
```

Or use the Makefile:

```bash
make build           # Same as ./gradlew build
make test            # Same as ./gradlew test
make install         # Build and install to local IDE
```
