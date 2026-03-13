## MODIFIED Requirements

### Requirement: ReadAction-protected VirtualFile reads

All plugin VirtualFile read operations SHALL be wrapped in `ReadAction.computeCancellable()` when executing on background threads. This ensures the IntelliJ read lock is held during VFS access and supports cooperative cancellation.

#### Scenario: Spec parsing reads use cancellable ReadAction
- **WHEN** `SpecParsingService.parseSpec()` reads a VirtualFile's content on a background thread
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.computeCancellable()`

#### Scenario: Change metadata reads use cancellable ReadAction
- **WHEN** `ChangeService.getChangesFromDir()` reads `.openspec.yaml` via `getInputStream()` on a background thread
- **THEN** the stream read SHALL be wrapped in `ReadAction.computeCancellable()`

#### Scenario: Config reload reads use cancellable ReadAction
- **WHEN** `ConfigService.reload()` reads `config.yaml` via `getInputStream()` on a background thread
- **THEN** the stream read SHALL be wrapped in `ReadAction.computeCancellable()`

#### Scenario: Tracking metadata reads use cancellable ReadAction
- **WHEN** `TrackingMetadataWriter.readYaml()` reads a VirtualFile's content
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.computeCancellable()`

#### Scenario: Validation file reads use cancellable ReadAction
- **WHEN** `BuiltInValidator` reads file content for validation on a background thread
- **THEN** the `contentsToByteArray()` call SHALL be wrapped in `ReadAction.computeCancellable()`

#### Scenario: ReadAction fallback in test context
- **WHEN** a VirtualFile read executes without an IntelliJ Application (unit test context)
- **THEN** the code SHALL fall back to direct NIO file access without `ReadAction`
