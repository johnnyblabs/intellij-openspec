## ADDED Requirements

### Requirement: Test infrastructure includes Mockito

The project's test dependencies SHALL include Mockito for mocking services and their dependencies.

#### Scenario: Mockito available in test classpath
- **WHEN** the Gradle build runs tests
- **THEN** `org.mockito:mockito-core` and `org.mockito:mockito-junit-jupiter` SHALL be available as test dependencies

### Requirement: ArtifactOrchestrationService DAG parsing tests

The test suite SHALL verify that ArtifactOrchestrationService correctly parses artifact DAG status from CLI output.

#### Scenario: Parses complete DAG from CLI JSON
- **WHEN** getArtifactStatus() processes valid CLI JSON with 4 artifacts in mixed states
- **THEN** the returned ChangeArtifactDag SHALL contain all 4 artifacts with correct statuses (DONE, READY, BLOCKED)

#### Scenario: Scaffolding override sets file-exists-but-placeholder to READY
- **WHEN** an artifact file exists but contains only placeholder/scaffolding content
- **THEN** the artifact status SHALL be overridden from DONE to READY

#### Scenario: Cache invalidation re-reads DAG
- **WHEN** invalidateCache() is called for a change and getArtifactStatus() is called again
- **THEN** the CLI SHALL be invoked again (not returning stale cached result)

### Requirement: ArtifactOrchestrationService Generate All tests

The test suite SHALL verify the Generate All orchestration loop behavior.

#### Scenario: Generates artifacts in dependency order
- **WHEN** generateAllRemaining() is called with 3 remaining artifacts
- **THEN** the listener SHALL receive onArtifactStarted callbacks in dependency order with correct index and total values

#### Scenario: Fires onAllComplete when chain finishes
- **WHEN** all artifacts generate successfully
- **THEN** the listener SHALL receive onArtifactCompleted for each artifact followed by exactly one onAllComplete

#### Scenario: Stops chain on API error
- **WHEN** DirectApiService.generate() throws an exception on the 2nd artifact
- **THEN** the listener SHALL receive onError with the failed artifact ID and exception, and no further onArtifactStarted calls SHALL occur

#### Scenario: Respects cancellation between artifacts
- **WHEN** cancelGenerateAll() is called after the 1st artifact completes
- **THEN** the listener SHALL receive onCancelled and no further artifacts SHALL be generated

#### Scenario: Completed artifacts preserved on cancel or error
- **WHEN** Generate All is cancelled or errors after completing some artifacts
- **THEN** previously completed artifacts SHALL remain written to disk (verified via file system)

### Requirement: DirectApiService provider tests

The test suite SHALL verify that DirectApiService correctly handles all three AI providers.

#### Scenario: isConfigured returns false with no API key
- **WHEN** no API key is stored for any provider
- **THEN** isConfigured() SHALL return false

#### Scenario: isConfigured returns true with valid API key
- **WHEN** an API key is stored for Claude provider
- **THEN** isConfigured() SHALL return true

#### Scenario: Generate returns content string on success
- **WHEN** generate() is called and the API returns a successful response
- **THEN** the method SHALL return the generated content as a string

#### Scenario: Generate throws AiApiException on API error
- **WHEN** generate() is called and the API returns an error status (401, 429, 500)
- **THEN** the method SHALL throw AiApiException with a descriptive message including the status code

### Requirement: ArtifactStatus enum tests

The test suite SHALL verify all ArtifactStatus enum values including the new GENERATING and ERROR states.

#### Scenario: All six statuses parse from strings
- **WHEN** fromString() is called with "done", "ready", "blocked", "generating", "error", "unknown"
- **THEN** each SHALL return the corresponding enum value

#### Scenario: Unknown strings default to UNKNOWN
- **WHEN** fromString() is called with null, empty, or unrecognized string
- **THEN** UNKNOWN SHALL be returned

#### Scenario: Each status has an icon
- **WHEN** toIcon() is called on each status value
- **THEN** each SHALL return a non-empty string

### Requirement: IssueLifecycleService graceful handling tests

The test suite SHALL verify that IssueLifecycleService handles missing tracker matches gracefully.

#### Scenario: No matching Forgejo issue skips silently
- **WHEN** the lifecycle service searches for a Forgejo issue and no match is found
- **THEN** no exception SHALL be thrown and the method SHALL return normally

#### Scenario: No matching Plane work item skips silently
- **WHEN** the lifecycle service searches for a Plane work item and no match is found
- **THEN** no exception SHALL be thrown and the method SHALL return normally

### Requirement: All existing tests continue to pass

The test suite changes SHALL NOT break any existing tests.

#### Scenario: Existing unit tests pass after changes
- **WHEN** `./gradlew test` is run after all test additions
- **THEN** all previously passing tests SHALL continue to pass

#### Scenario: New tests pass on clean build
- **WHEN** `./gradlew clean test` is run
- **THEN** all new tests SHALL pass alongside existing tests
