## Context

The plugin has 25 test files covering ~34% of source files by count. Unit tests exist for models, enums, tool classification, and parsing logic. Integration tests use IntelliJ's `BasePlatformTestCase` via `OpenSpecIntegrationTestBase` with fixture-based project setup.

**What's tested:** ConfigService, SpecParsingService, ChangeService, AiToolDetectionService (classification only), ScaffoldingDetectionService, BuiltInValidator, AiProvider, DeliveryMode, CliOutputParser, VersionSupport, ValidationResult.

**What's NOT tested (critical gaps):**
- `DirectApiService` — all AI API calls (Claude, OpenAI, Gemini)
- `ArtifactOrchestrationService` — DAG status, scaffolding overrides, Generate All loop
- `DeliveryMethodResolver` — resolution priority chain
- `CliRunner` / `CliDetectionService` — CLI execution and discovery
- `AiToolDetectionService.ToolGuidance` — per-tool metadata, guidance messages
- `WorkflowActionPanel` — no UI state tests at all
- `ForgejoService` / `PlaneService` / `IssueLifecycleService` — tracker integrations

**No mocking library** is available — Mockito is not in `build.gradle.kts`. This limits testability of services that depend on HTTP clients, IntelliJ APIs, or CLI processes.

The first users will be in a corporate environment where **Copilot skills are disabled**, meaning the plugin's fallback paths (Direct API promotion, clipboard delivery, guidance messaging) are the primary experience and must be thoroughly tested.

## Goals / Non-Goals

**Goals:**
- Add Mockito to test dependencies for mocking services and HTTP responses
- Cover all untested services with unit tests focused on logic branches
- Add user-scenario integration tests that simulate real configurations: skills disabled, no API key, CLI not found, Direct API configured, mixed tool detection
- Test `DeliveryMethodResolver` resolution priority chain exhaustively
- Test `ArtifactOrchestrationService` DAG processing and Generate All flow
- Test `ToolGuidance` metadata for all 6 tools + default fallback
- Verify the "skills disabled" user path works end-to-end: detection → delivery resolution → guidance messaging

**Non-Goals:**
- Testing actual HTTP calls to Claude/OpenAI/Gemini (mock responses only)
- Testing Swing UI rendering (no robot/screenshot tests)
- Achieving 100% line coverage — focus on behavior coverage for user scenarios
- Testing IntelliJ Platform SDK internals (PasswordSafe, VFS, etc.)
- End-to-end browser/IDE automation tests

## Decisions

### 1. Add Mockito for service mocking

Add `testImplementation("org.mockito:mockito-core:5.11.0")` and `testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")` to `build.gradle.kts`.

**Why:** Services like `DeliveryMethodResolver`, `ArtifactOrchestrationService`, and `DirectApiService` depend on IntelliJ project services (`OpenSpecSettings`, `AiToolDetectionService`, `CliRunner`). Without mocking, these are untestable in isolation.

**Alternative considered:** Manual test doubles (hand-written stubs) — rejected because the codebase uses `@Service` with constructor injection, and mocking the service registry is much simpler with Mockito than building a parallel set of fake services.

### 2. User-scenario test class structure

Create a dedicated `UserScenarioTest` class that tests complete configuration scenarios as integration tests:

| Scenario | Tools Detected | API Key | Skills | Expected Behavior |
|---|---|---|---|---|
| Corporate Copilot (skills disabled) | Copilot [IDE] | None | Disabled | Clipboard delivery, guidance says "Open Copilot Chat and paste" |
| Corporate Copilot + Direct API | Copilot [IDE] | Claude key | Disabled | Direct API delivery, Generate All visible |
| CLI power user | Claude Code [CLI] | None | N/A | Clipboard delivery, guidance says "Paste into Claude Code" |
| API-only user | None | OpenAI key | N/A | Direct API delivery, Generate All visible |
| Zero config | None | None | N/A | Clipboard fallback, "Configure an AI tool or API key" guidance |
| Multi-tool | Claude Code + Copilot | Claude key | Mixed | Direct API preferred, all tools in selector |

These tests verify the full resolution chain: tool detection → delivery method → guidance text → button visibility.

**Why scenario-based:** Individual unit tests for each service verify internal logic but don't catch integration seams. A user installing the plugin in a skills-disabled environment exercises detection → resolution → UI state as a chain. Scenario tests catch the gaps between services.

### 3. Test `DeliveryMethodResolver` with mocked dependencies

Unit test the 4-step priority chain:
1. Saved preference wins
2. Configured API provider falls through when no preference
3. Detected tools set clipboard mode with tool label
4. Bare fallback returns generic clipboard

Mock `OpenSpecSettings` and `AiToolDetectionService` to control each branch. This is the single most important unit test gap — it's the decision point for every user interaction.

### 4. Test `ArtifactOrchestrationService` DAG and Generate All logic

Unit test with mocked `CliRunner` (returns fixture JSON for `openspec status --json`):
- DAG parsing from CLI output
- Scaffolding override (file exists but is placeholder)
- `generateAllRemaining()` loop: fires listener callbacks in order, stops on error, respects cancellation flag
- Cache invalidation between artifacts

**Why mock CliRunner:** The orchestration logic is pure Java — parsing JSON, managing state, calling listeners. The CLI is just an I/O boundary. Mocking it isolates the logic we care about.

### 5. Test `ToolGuidance` metadata exhaustively

Parameterized test (`@ParameterizedTest` + `@MethodSource`) that verifies all 6 tools + null/unknown return correct `ToolGuidance` records:
- `chatPanelName` matches expected (terminal, Copilot Chat, Composer, etc.)
- `pasteAction` matches expected
- `promptPrefix` is correct (null for Cursor/Windsurf/Cline, `/opsx:` for Claude, `/opsx-` for Copilot)
- `canAutoSave` is true only for CLI tools

This directly validates the "skills disabled" experience — when `promptPrefix` is `/opsx-` but skills are disabled, the guidance should still work via clipboard.

### 6. Test `DirectApiService` with mocked HTTP

Unit test the three provider branches (Claude, OpenAI, Gemini) with mocked HTTP client responses:
- Successful generation returns content string
- API error (401, 429, 500) throws `AiApiException` with descriptive message
- Network timeout throws appropriate error
- `isConfigured()` returns false when no API key set

**Why not WireMock:** Adding a full HTTP mock server is overkill for 3 simple REST endpoints. Mockito can mock the `HttpClient` or the internal HTTP call method directly.

### 7. Don't test tracker services deeply

`ForgejoService`, `PlaneService`, and `IssueLifecycleService` are thin HTTP wrappers that call external APIs. Testing them requires either real API access or extensive HTTP mocking for minimal value. These are best tested manually during the archive workflow.

Add only a basic test that `IssueLifecycleService` correctly delegates to Forgejo/Plane services and handles "no match found" gracefully (returns without error).

## Risks / Trade-offs

- **Mockito + IntelliJ Platform SDK interaction** → Some IntelliJ services use `final` classes or static methods that Mockito can't mock. Mitigation: Use `mockito-inline` agent if needed, or test at the integration level with `BasePlatformTestCase` for those cases
- **Test fixture staleness** → CLI fixture JSON may drift from actual CLI output as OpenSpec evolves. Mitigation: `CliContractTest` pattern already exists — extend it to validate fixture freshness
- **Flaky integration tests** → IntelliJ test framework can be sensitive to VFS timing. Mitigation: Use `VfsUtil.markDirtyAndRefresh()` (already in base class) and avoid file-system-dependent assertions where possible
- **Over-mocking** → Mocking too many layers produces tests that pass but don't verify real behavior. Mitigation: Scenario tests use real services wired through IntelliJ DI; only mock at I/O boundaries (HTTP, CLI, file system)
