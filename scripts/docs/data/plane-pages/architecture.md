# Architecture Overview

## Plugin Architecture

OpenSpec Plugin is an IntelliJ IDEA plugin built on the IntelliJ Platform SDK (2024.2+). It follows the platform's service-oriented architecture with project-level services registered in `plugin.xml`.

### Service Layer

- **OpenSpecSettings** — `PersistentStateComponent` storing all plugin configuration (CLI path, AI provider, tracker settings)
- **ChangeService** — Manages the OpenSpec change lifecycle (list, create, archive changes)
- **DirectApiService** — AI provider abstraction supporting Claude, OpenAI, and Gemini
- **ForgejoService** — Forgejo REST API client for issue tracking
- **PlaneService** — Plane REST API client for work item management
- **IssueLifecycleService** — Orchestrates tracker operations across propose/apply/archive actions
- **CliDetectionService** — Scans PATH and common locations for the OpenSpec CLI executable
- **CliRunner** — Executes OpenSpec CLI commands with proper environment setup

### Key Patterns

- **Credential Storage**: `PasswordSafe` API via `AiCredentialStore` and `TrackerCredentialStore`
- **Background Execution**: `ApplicationManager.executeOnPooledThread()` for non-blocking operations
- **Tool Detection**: Scans for `.claude/`, `.cursor/`, `.github/` directories to detect AI tools
- **Delivery Modes**: CLI tools (Claude Code, Gemini CLI) vs IDE Panel tools (Copilot, Cursor, Windsurf)

### Module Structure

```
src/main/java/com/johnnyb/openspec/
├── actions/          # AnAction implementations (Propose, Apply, Archive, Generate)
├── model/            # Data models (ChangeInfo, ChangeMetadata, SchemaType)
├── services/         # Core services (CliDetection, CliRunner, DirectApi, Change)
├── settings/         # Settings panel, configurable, persistence
├── toolwindow/       # Tool window factory, spec tree model, workflow panel
├── tracking/         # Forgejo/Plane integration services
└── util/             # Shared utilities
```

## Spec-Driven Development

The plugin implements the OpenSpec workflow:
1. **Propose** — Create a change with proposal, design, and task artifacts
2. **Generate** — AI generates artifacts from the proposal
3. **Apply** — Implement tasks from generated artifacts
4. **Archive** — Complete and archive the change with spec sync
