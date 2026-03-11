## Why

When `config.yaml` or `.openspec.yaml` contains invalid YAML (typos, bad indentation, unclosed quotes), the plugin silently swallows the parse error — `ConfigService` logs to `idea.log` and returns `null`, while `ChangeService` warns and proceeds without metadata. The user sees broken behavior (missing changes, empty panels) with no indication that their YAML is malformed. The backlog item calls for "clear error notification instead of stack trace when config.yaml contains invalid YAML, including file path and parse error location."

## What Changes

- **Show user-facing error notifications** when YAML parsing fails in `ConfigService` and `ChangeService`, including the file path and the parse error location (line/column from SnakeYAML's `MarkedYAMLException`)
- **Add a config validation inspection** that catches YAML syntax errors in real-time as the user edits `config.yaml` or `.openspec.yaml`, showing inline error markers in the editor
- **Harden TrackingMetadataWriter** to use safe YAML loading (`LoaderOptions`) consistent with the rest of the codebase

## Capabilities

### New Capabilities
_(none)_

### Modified Capabilities
- `validation`: Add YAML syntax validation for `config.yaml` and `.openspec.yaml` files
- `plugin-core`: ConfigService and ChangeService show user-facing notifications on YAML parse failure

## Impact

- `src/main/java/com/johnnyb/openspec/services/ConfigService.java` — enhanced error handling in `reload()`
- `src/main/java/com/johnnyb/openspec/services/ChangeService.java` — enhanced error handling in `getChangesFromDir()`
- `src/main/java/com/johnnyb/openspec/tracking/TrackingMetadataWriter.java` — safe YAML loading
- `src/main/java/com/johnnyb/openspec/validation/ConfigValidationInspection.java` — YAML syntax checking
- No new dependencies (SnakeYAML already provides `MarkedYAMLException` with line/column info)
