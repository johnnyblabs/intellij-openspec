## ADDED Requirements

### Requirement: ReadAction-protected VirtualFile reads

All plugin VirtualFile read operations SHALL be wrapped in `ReadAction.compute()` when executing on background threads. This ensures the IntelliJ read lock is held during VFS access, preventing stale reads and assertion errors during concurrent write actions.

#### Scenario: Spec parsing reads use ReadAction
- **WHEN** `SpecParsingService.parseSpec()` reads a VirtualFile's content on a background thread
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.compute()`

#### Scenario: Change metadata reads use ReadAction
- **WHEN** `ChangeService.getChangesFromDir()` reads `.openspec.yaml` via `getInputStream()` on a background thread
- **THEN** the stream read SHALL be wrapped in `ReadAction.compute()`

#### Scenario: Config reload reads use ReadAction
- **WHEN** `ConfigService.reload()` reads `config.yaml` via `getInputStream()` on a background thread
- **THEN** the stream read SHALL be wrapped in `ReadAction.compute()`

#### Scenario: Tracking metadata reads use ReadAction
- **WHEN** `TrackingMetadataWriter.readYaml()` reads a VirtualFile's content
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.compute()`

#### Scenario: Validation file reads use ReadAction
- **WHEN** `BuiltInValidator` reads file content for validation on a background thread
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.compute()`

#### Scenario: ReadAction fallback in test context
- **WHEN** a VirtualFile read executes without an IntelliJ Application (unit test context)
- **THEN** the code SHALL fall back to direct NIO file access without `ReadAction`

### Requirement: Thread-safe ConfigService initialization

The `ConfigService` SHALL use volatile field access and synchronized initialization to prevent race conditions when multiple threads call `getConfig()` concurrently.

#### Scenario: Concurrent getConfig calls
- **WHEN** multiple threads call `ConfigService.getConfig()` simultaneously and `config` is null
- **THEN** exactly one thread SHALL execute `reload()`
- **AND** other threads SHALL wait for and receive the loaded config

#### Scenario: Fast path after initialization
- **WHEN** `getConfig()` is called after config has been loaded
- **THEN** the method SHALL return the cached config without acquiring any lock
