## Why

Multiple plugin services read VirtualFiles from background threads without `ReadAction` wrappers, and `ConfigService` has a race condition on its `config` field. While the existing write-side patterns are solid (WriteAction.runAndWait used correctly), the read-side gaps can cause stale reads or assertion errors under concurrent operations like refresh + validate + generate running in parallel.

## What Changes

- Wrap all VirtualFile read operations in `ReadAction.compute()` across 5 services/classes
- Make `ConfigService.config` field volatile and add double-checked locking to `getConfig()`
- Wrap `ChangeService.getInputStream()` and `getChangesFromDir()` VirtualFile reads in ReadAction
- Wrap `SpecParsingService.parseSpec()` VirtualFile read in ReadAction
- Wrap `TrackingMetadataWriter.readYaml()` in ReadAction
- Wrap `BuiltInValidator.readFile()` in ReadAction
- Wrap `ConfigService.reload()` VirtualFile reads in ReadAction

## Capabilities

### New Capabilities

_(none — this is a hardening change)_

### Modified Capabilities

- `plugin-core`: Add requirement for ReadAction on VirtualFile reads and thread-safe ConfigService initialization

## Impact

- **Services affected**: ConfigService, ChangeService, SpecParsingService, TrackingMetadataWriter, BuiltInValidator
- **Risk**: Low — wrapping reads in ReadAction is additive and backward-compatible
- **Testing**: Existing tests continue to pass; unit tests don't run under IntelliJ Application so ReadAction fallback may be needed (same pattern as WriteAction fallback in ArtifactOrchestrationService)
