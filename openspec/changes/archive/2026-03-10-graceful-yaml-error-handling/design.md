## Context

The plugin parses YAML in three places: `ConfigService.reload()` for `config.yaml`, `ChangeService.getChangesFromDir()` for `.openspec.yaml`, and `TrackingMetadataWriter.readYaml()` for tracking metadata updates. All three use SnakeYAML. When parsing fails, `ConfigService` logs ERROR and returns null; `ChangeService` logs WARN and skips metadata; `TrackingMetadataWriter` lets `IOException` propagate.

SnakeYAML's `MarkedYAMLException` already carries the exact line/column of the parse error plus a human-readable problem description — this information is just being discarded.

## Goals / Non-Goals

**Goals:**
- Show clear, user-facing notifications when YAML parsing fails, with file path and error location
- Add real-time YAML syntax validation in the editor for `config.yaml` and `.openspec.yaml`
- Harden `TrackingMetadataWriter` to use safe YAML loading
- Distinguish between "file not found" and "file is malformed" in all error paths

**Non-Goals:**
- Schema-level validation of YAML content (e.g., "profile must have a name field") — existing `BuiltInValidator` already handles this
- Supporting YAML alternatives (TOML, JSON config files)
- Auto-fixing malformed YAML

## Decisions

### 1. Extract error details from MarkedYAMLException

**Decision:** Catch `MarkedYAMLException` specifically (not generic `Exception`) in `ConfigService` and `ChangeService`. Extract `getProblem()`, `getProblemMark().getLine()`, and `getProblemMark().getColumn()` to build a descriptive error message.

**Rationale:** SnakeYAML already computes the exact error location. A message like "config.yaml: line 5, column 3: mapping values are not allowed here" is immediately actionable. The current "Failed to parse" message is not.

**Alternative considered:** Parsing the exception message string — rejected because `MarkedYAMLException` has structured accessors.

### 2. User notification via OpenSpecNotifier

**Decision:** Use `OpenSpecNotifier.warn()` for YAML parse failures. Include a clickable file path in the notification content so users can navigate directly to the file.

**Rationale:** Consistent with how tracker failures and other non-fatal errors are reported. WARN level is correct — the plugin continues to function but with degraded state (no config loaded, change without metadata).

### 3. Enhance ConfigValidationInspection for YAML syntax

**Decision:** Extend the existing `ConfigValidationInspection` to attempt YAML parsing and report syntax errors as IDE problems (red squiggles). Apply to both `config.yaml` and `.openspec.yaml` files.

**Rationale:** The inspection already runs on these files and checks for required fields via text matching. Adding YAML parse validation catches errors earlier (while editing) rather than later (when the service loads the file). Reuses the existing inspection registration in `plugin.xml`.

**Alternative considered:** A separate `YamlSyntaxInspection` — rejected because it would overlap with `ConfigValidationInspection` on the same files.

### 4. Safe YAML loading in TrackingMetadataWriter

**Decision:** Replace `new Yaml()` with `new Yaml(new LoaderOptions())` in `TrackingMetadataWriter.readYaml()`, matching the pattern used in `ConfigService` and `ChangeService`.

**Rationale:** While the untyped `yaml.load()` call in TrackingMetadataWriter is lower-risk (it reads `.openspec.yaml` which the plugin itself writes), using `LoaderOptions` is the SnakeYAML 2.x best practice and prevents potential deserialization issues if the file is externally modified.

## Risks / Trade-offs

- **[Notification fatigue]** → If the user has multiple malformed YAML files, they'll see multiple notifications. Mitigation: each notification includes the specific file path so they're not confusing, and they're shown once per load, not continuously.
- **[Inspection performance]** → YAML parsing in the inspection runs on every file save. Mitigation: SnakeYAML parsing is fast (< 1ms for small files), and the inspection only targets `config.yaml` and `.openspec.yaml` files.
