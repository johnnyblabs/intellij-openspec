# OpenSpec IntelliJ Plugin — Copilot Instructions

## What This Project Is

An IntelliJ IDEA plugin for the OpenSpec spec-driven development framework. It lets users browse specs, manage changes, generate artifacts with AI, and validate — all from within the IDE.

- **Language**: Java 21
- **Framework**: IntelliJ Platform SDK 2024.2+
- **Build**: Gradle with IntelliJ Platform Gradle Plugin 2.11.0
- **Schema**: spec-driven (OpenSpec workflow profile)

## Project Structure

```
intellij-openspec/
├── openspec/                          # OpenSpec project metadata
│   ├── config.yaml                    # Schema, profile, rules
│   ├── specs/                         # Main specifications (source of truth)
│   │   ├── actions/spec.md
│   │   ├── ai-setup/spec.md
│   │   ├── editor/spec.md
│   │   ├── plugin-core/spec.md
│   │   ├── scaffolding-detection/spec.md
│   │   ├── tool-window/spec.md
│   │   ├── validation/spec.md
│   │   └── workflow-panel/spec.md
│   └── changes/                       # Active and archived changes
│       ├── <change-name>/             # Active changes
│       │   ├── .openspec.yaml         # Change metadata
│       │   ├── proposal.md
│       │   ├── design.md
│       │   ├── tasks.md
│       │   └── specs/                 # Delta specs (diffs to main specs)
│       └── archive/                   # Completed changes (date-prefixed)
├── src/main/java/com/johnnyb/openspec/
│   ├── actions/                       # IntelliJ actions (menu items, toolbar)
│   ├── ai/                            # AI provider integration (Claude, OpenAI)
│   ├── dialogs/                       # UI dialogs
│   ├── model/                         # Data models (Change, ArtifactInfo, etc.)
│   ├── scaffolding/                   # Scaffolding file generation
│   ├── services/                      # Project services (core logic)
│   ├── settings/                      # Plugin settings (PersistentStateComponent)
│   ├── toolwindow/                    # Tool window UI (tree, panels)
│   ├── util/                          # Utilities (CLI runner, file utils)
│   ├── validation/                    # Built-in validation
│   └── version/                       # Version handling
├── src/main/resources/META-INF/
│   └── plugin.xml                     # Plugin descriptor (actions, services, extensions)
└── src/test/java/                     # Unit tests (JUnit 5)
```

## OpenSpec CLI

The plugin integrates with the `openspec` CLI (`npm i -g @fission-ai/openspec`). Key commands:

```bash
openspec init                                    # Initialize project
openspec new change "<name>"                     # Create a new change
openspec list --json                             # List all changes
openspec status --change "<name>" --json         # Artifact DAG status
openspec instructions <artifact> --change "<name>" --json  # Get AI generation instructions
openspec instructions apply --change "<name>" --json       # Get implementation instructions
openspec validate --all                          # Validate specs and changes
```

## Artifact DAG (Dependency Graph)

Each change has artifacts that must be generated in order:

```
proposal → design ──→ tasks
         → specs  ──↗
```

- **proposal.md** — What and why (always first)
- **design.md** — How (depends on proposal)
- **specs/** — Delta specs with new/modified requirements (depends on proposal)
- **tasks.md** — Implementation steps (depends on design + specs)

Artifact statuses: `done` (file exists with real content), `ready` (dependencies met), `blocked` (waiting on dependencies).

The plugin has scaffolding detection — if a file exists but only contains placeholder headings/comments, it's treated as `ready`, not `done`.

## Coding Conventions

- All services use `@Service(Service.Level.PROJECT)` — registered in `plugin.xml`
- Settings use `PersistentStateComponent` with `@State` annotation
- Specs use RFC 2119 keywords (SHALL, SHOULD, MAY) with Given-When-Then scenarios
- Change names are kebab-case (e.g., `add-user-auth`)
- Plugin supports IntelliJ IDEA 2024.2+
- After archiving a change: commit all changes and push to remote

## Available Custom Prompts

This project includes reusable prompts in `.github/prompts/`:

- `/opsx-propose` — Propose a new change with all artifacts
- `/opsx-apply` — Implement tasks from a change
- `/opsx-explore` — Think through ideas and explore the codebase
- `/opsx-archive` — Archive a completed change

These prompts use the `openspec` CLI to drive the workflow. When using them:
1. The CLI must be installed (`openspec` on PATH)
2. Run CLI commands in the terminal to get JSON output
3. Read/write files in the `openspec/changes/` directory
4. Follow the artifact DAG order

## Key Services

| Service | Purpose |
|---------|---------|
| `OpenSpecProjectService` | Main entry point, initializes on project open |
| `ConfigService` | Reads `openspec/config.yaml` |
| `ChangeService` | Manages changes (list, status) |
| `ArtifactOrchestrationService` | Artifact DAG, generation, scaffolding overrides |
| `AiToolDetectionService` | Detects AI tools (Claude Code, Copilot, Cursor, etc.) |
| `ScaffoldingDetectionService` | Detects placeholder content vs real content |
| `CliDetectionService` | Finds and validates the `openspec` CLI |
| `CliRunner` | Executes CLI commands |

## AI Tool Classification

The plugin classifies detected AI tools by type:

| Tool | Type | Behavior |
|------|------|----------|
| Claude Code | CLI | Can save files directly; guidance says "it will save automatically" |
| Gemini | CLI | Same as above |
| GitHub Copilot | IDE_PANEL | User must copy response and save; guidance says "copy and save to:" |
| Cursor | IDE_PANEL | Same as above |
| Windsurf | IDE_PANEL | Same as above |
| Cline | IDE_PANEL | Same as above |
