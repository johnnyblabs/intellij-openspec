# Development Guide

## Prerequisites

- **Java 21** (JDK)
- **IntelliJ IDEA** 2024.2+ (Community or Ultimate)
- **Gradle** (wrapper included)
- **OpenSpec CLI** installed and on PATH

## Getting Started

### Clone and Build

```bash
git clone ssh://git@geek:222/johnb/OpenSpecPlugin.git
cd OpenSpecPlugin
./gradlew build
```

### Run in Development

```bash
./gradlew runIde
```

This launches a sandboxed IntelliJ instance with the plugin installed.

### Run Tests

```bash
./gradlew test
```

## Project Structure

- `src/main/java/` — Plugin source code
- `src/main/resources/META-INF/plugin.xml` — Plugin descriptor and service registration
- `src/test/java/` — Unit tests
- `openspec/` — OpenSpec specs and change management
- `scripts/` — Setup and sync scripts for Forgejo and Plane

## Making Changes

All changes follow the OpenSpec workflow:

1. **Propose**: `/opsx:propose <change-name>` — creates proposal, design, and tasks
2. **Apply**: `/opsx:apply` — implement tasks from the generated artifacts
3. **Archive**: `/opsx:archive` — archive the completed change

## Environment Setup

Copy `scripts/.env.example` to `scripts/.env` and configure:

- `FORGEJO_TOKEN` — Forgejo API token
- `FORGEJO_URL` — Forgejo server URL
- `PLANE_API_KEY` — Plane API key
- `PLANE_URL` — Plane server URL
- `PLANE_WORKSPACE` — Plane workspace slug

Run `scripts/setup-tokens.sh` for interactive setup.

## Key Conventions

- Services use `@Service(Service.Level.PROJECT)` pattern
- Settings persist via `PersistentStateComponent`
- Credentials stored in IntelliJ `PasswordSafe`
- Specs use RFC 2119 keywords (SHALL, SHOULD, MAY)
- Change names use kebab-case
