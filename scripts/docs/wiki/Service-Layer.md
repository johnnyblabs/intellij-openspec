# Service Layer

All services are registered as `@Service(Service.Level.PROJECT)` and accessed via `project.getService(Class)`. The `OpenSpecProjectService` acts as a facade.

## OpenSpecProjectService

**Role:** Facade and entry point for all plugin services. Includes startup activity.

**Inner class:** `StartupDetection` — runs at project open to detect CLI and AI tools.

**Key methods:**
| Method | Returns |
|--------|---------|
| `isOpenSpecProject()` | `boolean` — checks if `openspec/` directory exists |
| `getConfigService()` | `ConfigService` |
| `getSpecParsingService()` | `SpecParsingService` |
| `getChangeService()` | `ChangeService` |
| `getCliDetectionService()` | `CliDetectionService` |
| `getAiToolDetectionService()` | `AiToolDetectionService` |
| `getArtifactOrchestrationService()` | `ArtifactOrchestrationService` |
| `getSettings()` | `OpenSpecSettings` |

## ConfigService

**Role:** Loads and caches `openspec/config.yaml`.

**Dependencies:** SnakeYAML (bundled with IntelliJ)

| Method | Description |
|--------|-------------|
| `getConfig()` | Returns cached `OpenSpecConfig` or loads from disk |
| `reload()` | Forces re-read from disk |
| `isConfigLoaded()` | Whether config has been successfully loaded |

## SpecParsingService

**Role:** Parses `spec.md` files into `SpecFile` objects with a line-oriented scanner that mirrors the OpenSpec CLI's own parser (not a general markdown/AST parser).

**Dependencies:** VirtualFile API

| Method | Description |
|--------|-------------|
| `parseAllSpecs()` | Scans `openspec/specs/` and parses all `.md` files |
| `parseSpec(VirtualFile)` | Parses a single spec file |
| `parseSpecContent(String, String)` | Parses raw markdown content |

**Parsing extracts:** domain (from directory name), title (first level-1 `#` heading), requirements (non-fenced `### Requirement:` headers, case-insensitive token), scenarios (any non-fenced level-4 `####` header), and the normative keyword.

**CLI-parity recognition rules** (`align-spec-parser-with-cli`): a per-line code-fence mask is built first and applied before every structural match, so markers inside ` ``` `/`~~~` fences are ignored; **any** level-4 header is a scenario (the bold `**Scenario:**` form is not); the normative keyword is the whole-word, case-sensitive `SHALL`/`MUST` evaluated on the requirement **body** only (`SHOULD`/`MAY` are not normative). Parity is proven against captured CLI output (`SpecParserCliStructureContractTest` vs `fixtures/cli/1.6.0/spec-structure/`) and against the retired regex parser (`SpecParserRegressionParityTest`). Note: this is a separate parse path from `BuiltInValidator`'s own fence mask; unifying them is a tracked follow-up.

## ChangeService

**Role:** Manages the lifecycle of changes (proposed → applied → archived).

**Dependencies:** `VersionSupport`, `OpenSpecSettings`

| Method | Description |
|--------|-------------|
| `getActiveChanges()` | Lists changes in `openspec/changes/` (non-archive) |
| `getArchivedChanges()` | Lists changes in `openspec/changes/archive/` |
| `getStatus(Change)` | Reads status from `.openspec.yaml` |
| `updateStatus(Change, ChangeStatus)` | Writes new status to metadata file |
| `archiveChange(Change)` | Moves change directory to archive |
| `getMissingArtifacts(Change)` | Compares present files to version-required artifacts |
| `getDeltaSpecNames(Change)` | Lists delta spec files in a change |

## CliDetectionService

**Role:** Locates the OpenSpec CLI binary.

**Dependencies:** `GeneralCommandLine`, `OSProcessHandler`

| Method | Description |
|--------|-------------|
| `detect()` | Runs the detection cascade; caches result |
| `isAvailable()` | Whether CLI was found |
| `getDetectedPath()` | Absolute path to CLI binary |
| `getDetectedVersion()` | CLI version string |
| `tryPath(String)` | Tests if a specific path is a valid CLI |

**Detection cascade:**
1. Settings-configured path
2. Bare `openspec` on PATH
3. Login shell resolution
4. Common installation paths

## ArtifactOrchestrationService

**Role:** Manages artifact dependency graphs via CLI output.

**Dependencies:** `CliRunner`, `CliOutputParser`

| Method | Description |
|--------|-------------|
| `getArtifactStatus(String changeName)` | Returns `ChangeArtifactDag` for a change |
| `getInstruction(String changeName, String artifactId)` | Returns `ArtifactInstruction` with full prompt context |
| `getGenerationOrder(String changeName)` | Returns artifacts in dependency order |
| `invalidateCache(String changeName)` | Clears cached DAG for a change |
| `invalidateAllCaches()` | Clears all cached DAGs |

## AiToolDetectionService

**Role:** Scans project root for known AI tool configuration directories.

| Method | Description |
|--------|-------------|
| `detect()` | Scans for `.claude/`, `.github/copilot/`, `.cursor/`, `.windsurf/`, `.cline/` |
| `getDetectedTools()` | List of tool names found |
| `getSummary()` | Human-readable summary for status bar |
| `hasDetectedTools()` | Whether any AI tools were found |

## Supporting Services (registered in plugin.xml)

| Service | Package | Role |
|---------|---------|------|
| `DirectApiService` | ai | Calls AI provider APIs |
| `AiCredentialStore` | ai | Secure API key storage |
| `BuiltInValidator` | validation | Rule-based validation |
| `ScaffoldingService` | scaffolding | File/directory creation |
| `OpenSpecSettings` | settings | Persistent settings storage |
| `OpenSpecConsoleService` | toolwindow | Console tab access |

---

**Previous:** [[Package-Reference]] | **Next:** [[Actions-and-Commands]]
