# Tasks: ensure-integration-testing

## 1. Add Mockito dependency

- [x] Add `testImplementation("org.mockito:mockito-core:5.11.0")` to `build.gradle.kts`
- [x] Add `testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")` to `build.gradle.kts`
- [x] Run `./gradlew dependencies --configuration testCompileClasspath` to verify Mockito resolves

## 2. ArtifactStatus enum tests

- [x] Create `src/test/java/com/johnnyb/openspec/model/ArtifactStatusTest.java`
- [x] Test `fromString()` parses all six values: "done", "ready", "blocked", "generating", "error", "unknown"
- [x] Test `fromString()` returns UNKNOWN for null, empty string, and unrecognized input
- [x] Test `toIcon()` returns a non-empty string for each enum value

## 3. ToolGuidance metadata tests

- [x] Create `src/test/java/com/johnnyb/openspec/services/ToolGuidanceTest.java`
- [x] Add `@ParameterizedTest` + `@MethodSource` covering all 6 tools: Claude Code, Gemini, GitHub Copilot, Cursor, Windsurf, Cline
- [x] Assert each tool returns non-null ToolGuidance with non-empty `chatPanelName` and `pasteAction`
- [x] Assert CLI tools (Claude Code, Gemini) have `canAutoSave == true`
- [x] Assert IDE panel tools (GitHub Copilot, Cursor, Windsurf, Cline) have `canAutoSave == false`
- [x] Assert `promptPrefix` correctness: `/opsx:` for Claude Code, `/opsx-` for GitHub Copilot, `null` for Cursor/Windsurf/Cline
- [x] Test default guidance for null, empty string, and unknown tool name returns `chatPanelName "your AI tool"`, `promptPrefix null`, `canAutoSave false`

## 4. DeliveryMethodResolver priority chain tests

- [x] Create `src/test/java/com/johnnyb/openspec/services/DeliveryMethodResolverTest.java`
- [x] Mock `OpenSpecSettings` and `AiToolDetectionService` dependencies
- [x] Test: saved preference EDITOR_TAB wins over API key and detected tools
- [x] Test: API provider (Claude configured) takes second priority when no saved preference
- [x] Test: detected tool (Copilot) sets CLIPBOARD mode with tool-specific label when no preference and no API
- [x] Test: bare fallback returns CLIPBOARD with label "Copy to Clipboard" when nothing configured
- [x] Test: invalid saved preference falls through to next priority step

## 5. ArtifactOrchestrationService DAG parsing tests

- [x] Create `src/test/java/com/johnnyb/openspec/services/ArtifactOrchestrationServiceTest.java`
- [x] Mock `CliRunner` to return fixture JSON for `openspec status --json`
- [x] Test: parses complete DAG from CLI JSON with 4 artifacts in mixed states (DONE, READY, BLOCKED)
- [x] Test: scaffolding override sets file-exists-but-placeholder artifact from DONE to READY
- [x] Test: cache invalidation — `invalidateCache()` causes next `getArtifactStatus()` to re-invoke CLI

## 6. ArtifactOrchestrationService Generate All tests

- [x] Mock `DirectApiService` and `GenerateAllListener` for Generate All loop tests
- [x] Test: `generateAllRemaining()` fires `onArtifactStarted` callbacks in dependency order with correct index/total
- [x] Test: all artifacts completing fires `onArtifactCompleted` for each followed by exactly one `onAllComplete`
- [x] Test: API error on 2nd artifact fires `onError` with artifact ID and exception, no further `onArtifactStarted`
- [x] Test: `cancelGenerateAll()` after 1st artifact fires `onCancelled`, no further generation
- [x] Test: completed artifacts remain written to disk after cancel or error

## 7. DirectApiService provider tests

- [x] Create `src/test/java/com/johnnyb/openspec/services/DirectApiServiceTest.java`
- [x] Mock HTTP client or internal HTTP call method
- [x] Test: `isConfigured()` returns false when no API key stored
- [x] Test: `isConfigured()` returns true when Claude API key stored
- [x] Test: `generate()` returns content string on successful API response
- [x] Test: `generate()` throws `AiApiException` with descriptive message on 401, 429, 500 status codes

## 8. IssueLifecycleService graceful handling tests

- [x] Create `src/test/java/com/johnnyb/openspec/tracking/IssueLifecycleServiceTest.java`
- [x] Mock Forgejo and Plane service dependencies
- [x] Test: no matching Forgejo issue — method returns normally without exception
- [x] Test: no matching Plane work item — method returns normally without exception

## 9. User scenario integration tests

- [x] Create `src/test/java/com/johnnyb/openspec/integration/UserScenarioTest.java`
- [x] Corporate Copilot (skills disabled): Copilot detected, no API key → CLIPBOARD mode, label "Copy for GitHub Copilot", Generate All hidden
- [x] Corporate Copilot + Direct API: Copilot detected, Claude key → DIRECT_API mode, Generate All visible
- [x] CLI power user: Claude Code detected, no API key → CLIPBOARD mode, label "Copy for Claude Code", `canAutoSave true`, `chatPanelName "terminal"`
- [x] API-only user: no tools detected, OpenAI key → DIRECT_API mode, label "Generate via OpenAI", Generate All visible
- [x] Zero configuration: no tools, no API → CLIPBOARD mode, label "Copy to Clipboard", Generate All hidden
- [x] Multi-tool: Claude Code + Copilot detected, Claude key → DIRECT_API preferred; saved CLIPBOARD preference overrides

## 10. Regression verification

- [x] Run `./gradlew clean test` — all existing tests continue to pass
- [x] Run `./gradlew clean test` — all new tests pass alongside existing tests
