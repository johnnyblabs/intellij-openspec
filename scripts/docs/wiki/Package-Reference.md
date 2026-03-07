# Package Reference

The plugin contains 13 packages with 61 Java source files. This page documents every package and its classes.

## com.johnnyb.openspec.actions (13 classes)

User-facing actions triggered from menus, toolbar, and context menus.

| Class | Description |
|-------|-------------|
| `OpenSpecBaseAction` | Abstract base; checks `isOpenSpecProject()` in `update()` |
| `OpenSpecCliAction` | Abstract base for CLI-backed actions; runs CLI with fallback |
| `OpenSpecInitAction` | Initializes OpenSpec project structure |
| `OpenSpecProposeAction` | Creates a new change via dialog |
| `OpenSpecApplyAction` | Marks a change as applied (built-in only) |
| `OpenSpecArchiveAction` | Archives a change to `archive/` directory |
| `OpenSpecValidateAction` | Runs built-in + CLI validation |
| `OpenSpecListAction` | Lists specs and changes |
| `OpenSpecRefreshAction` | Refreshes the tool window tree |
| `GenerateArtifactAction` | Generates a single artifact with mode selection |
| `GenerateAllArtifactsAction` | Auto-generates all remaining artifacts via API |
| `CreateDeltaSpecAction` | Creates a new delta spec file |
| `OpenSpecDataKeys` | DataKey constants: `CHANGE_NAME`, `ARTIFACT_ID` |

## com.johnnyb.openspec.services (7 classes)

Core business logic, all registered as project-level services.

| Class | Description |
|-------|-------------|
| `OpenSpecProjectService` | Facade service; access point for all other services |
| `ConfigService` | Loads and caches `config.yaml` via SnakeYAML |
| `SpecParsingService` | Parses `spec.md` files using regex patterns |
| `ChangeService` | Manages change lifecycle (active/archived) |
| `CliDetectionService` | Auto-detects CLI availability and path |
| `ArtifactOrchestrationService` | Manages artifact DAG and generation order |
| `AiToolDetectionService` | Detects AI tool configurations in project |

## com.johnnyb.openspec.model (11 classes)

Data transfer objects, enums, and value types.

| Class | Type | Description |
|-------|------|-------------|
| `SpecFile` | Class | Parsed spec with domain, title, requirements |
| `Requirement` | Class | Requirement with name, body, RFC 2119 keyword, scenarios |
| `Scenario` | Class | Scenario with name and GIVEN/WHEN/THEN clauses |
| `Change` | Class | Change with name, path, metadata, artifact files |
| `ChangeMetadata` | Class | YAML-serializable metadata from `.openspec.yaml` |
| `ChangeStatus` | Enum | `PROPOSED`, `APPLIED`, `ARCHIVED`, `UNKNOWN` |
| `OpenSpecConfig` | Class | YAML-serializable `config.yaml` model |
| `ArtifactInfo` | Class | Artifact metadata (id, path, status, dependencies) |
| `ArtifactStatus` | Enum | `DONE`, `READY`, `BLOCKED`, `UNKNOWN` |
| `ArtifactInstruction` | Class | Full instruction for generating an artifact |
| `ChangeArtifactDag` | Class | Artifact dependency graph for a change |

## com.johnnyb.openspec.toolwindow (6 classes)

Tool window UI components.

| Class | Description |
|-------|-------------|
| `OpenSpecToolWindowFactory` | Creates Browse + Console tabs |
| `OpenSpecToolWindowPanel` | Main tree view with actions and file listener |
| `SpecTreeModel` | Builds tree structure from specs/changes/artifacts |
| `SpecTreeCellRenderer` | Custom rendering with icons, colors, and fonts |
| `OpenSpecConsolePanel` | Wraps IntelliJ `ConsoleView` for CLI output |
| `OpenSpecConsoleService` | Service to access and activate the console |

## com.johnnyb.openspec.util (4 classes)

Helper utilities.

| Class | Description |
|-------|-------------|
| `CliRunner` | Executes CLI commands with timeout and output capture |
| `OpenSpecFileUtil` | File/directory helpers (find specs, changes, config) |
| `CliOutputParser` | Parses JSON and text output from CLI |
| `OpenSpecNotifier` | Shows IntelliJ notification balloons |

## com.johnnyb.openspec.validation (6 classes)

Spec, change, and config validation.

| Class | Description |
|-------|-------------|
| `BuiltInValidator` | Validates specs, changes, and config against rules |
| `ValidationResult` | Immutable result: passed, issues list, source |
| `ValidationIssue` | Immutable issue: severity, path, line, message, rule |
| `SpecFormatInspection` | Inline editor inspection for spec files |
| `DeltaSpecInspection` | Inline editor inspection for delta specs |
| `ConfigValidationInspection` | YAML inspection for `config.yaml` |

## com.johnnyb.openspec.ai (5 classes)

AI provider integration.

| Class | Type | Description |
|-------|------|-------------|
| `DirectApiService` | Service | Calls Claude/OpenAI APIs directly |
| `AiProvider` | Enum | `NONE`, `CLAUDE`, `OPENAI` with model lists |
| `AiCredentialStore` | Service | Stores API keys in IntelliJ PasswordSafe |
| `AiApiException` | Exception | Custom exception for API errors |
| `DeliveryMode` | Enum | `CLIPBOARD`, `EDITOR_TAB`, `DIRECT_API` |

## com.johnnyb.openspec.settings (3 classes)

Plugin settings storage and UI.

| Class | Description |
|-------|-------------|
| `OpenSpecSettings` | PersistentStateComponent for all plugin settings |
| `OpenSpecSettingsPanel` | UI form for the settings page |
| `OpenSpecConfigurable` | Settings → Tools → OpenSpec entry point |

## com.johnnyb.openspec.scaffolding (2 classes)

File and directory scaffolding for init and propose.

| Class | Description |
|-------|-------------|
| `ScaffoldingService` | Creates directory structures and files via WriteAction |
| `TemplateProvider` | Provides Markdown templates for all artifact types |

## com.johnnyb.openspec.editor (3 classes)

Editor enhancements.

| Class | Description |
|-------|-------------|
| `SpecAnnotator` | Highlights RFC 2119 keywords (MUST, SHOULD, etc.) |
| `ScenarioAnnotator` | Highlights GIVEN/WHEN/THEN/AND keywords |
| `OpenSpecLineMarkerProvider` | Gutter icons for requirements |

## com.johnnyb.openspec.dialogs (1 class)

| Class | Description |
|-------|-------------|
| `ProposeChangeDialog` | Dialog for entering change name and description |

## com.johnnyb.openspec.version (1 class)

| Class | Type | Description |
|-------|------|-------------|
| `VersionSupport` | Enum | `v1_0`, `v1_1`, `v1_2` — defines required fields, artifacts, schemas per version |

---

**Previous:** [[Architecture-Overview]] | **Next:** [[Service-Layer]]
