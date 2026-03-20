## Context

The plugin currently hardcodes the `spec-driven` schema for all change operations. The OpenSpec CLI (v1.x) supports custom schemas via three commands: `openspec schemas --json` (list available schemas), `openspec schema fork <source> <name>` (fork an existing schema), and `openspec schema init <name>` (create a new schema from scratch). These commands are marked `[experimental]` in the CLI.

The existing architecture wraps CLI commands in project-level services (e.g., `ChangeService`, `SpecSyncService`) using `CliRunner` for process execution and JSON parsing for structured output. Settings are persisted via `OpenSpecSettings` (`PersistentStateComponent`) and the UI is built in `OpenSpecSettingsPanel` using `FormBuilder`. Dialogs extend `DialogWrapper` with validation support.

The parent change (v030-openspec-workflows) established the phasing plan. This is Phase 3, task group 6, implementing the schema management slice.

## Goals / Non-Goals

**Goals:**
- Expose schema list, fork, and init CLI commands through a new `SchemaService`
- Add a Schemas section to the Settings panel with a list of available schemas and a default selector
- Allow forking an existing schema and opening the result in the editor
- Provide a New Schema dialog for creating schemas with name, description, and artifact selection
- Add a schema dropdown to `ProposeChangeDialog` and `FfDialog` when multiple schemas exist
- Test all CLI interactions with mock-based unit tests

**Non-Goals:**
- Schema editing UI (inline YAML editor) — users edit forked schemas in the standard editor
- Schema validation or linting — the CLI handles this
- Schema sharing or publishing — out of scope for IDE plugin
- Migration from one schema to another for existing changes

## Decisions

### Decision 1: SchemaService as a thin CLI wrapper with caching

`SchemaService` wraps the three CLI commands and caches the schema list for the duration of a settings panel session. The cache is invalidated on fork or init operations.

**Why over alternative (parse schema YAML directly):** The CLI is the source of truth for schema resolution (inheritance, defaults, built-in schemas). Parsing YAML directly would duplicate logic and break when the CLI evolves. The CLI's `--json` flag provides stable structured output.

**Implementation:**
- `SchemaService` (`@Service(Service.Level.PROJECT)`) with three public methods:
  - `List<SchemaInfo> listSchemas()` — calls `openspec schemas --json`, parses JSON array
  - `String forkSchema(String source, String name)` — calls `openspec schema fork <source> <name>`, returns path to forked file
  - `String initSchema(String name)` — calls `openspec schema init <name>`, returns path to new schema file
- `SchemaInfo` record: `name`, `description`, `isBuiltIn`, `artifactIds`
- Cache: `listSchemas()` result cached in a field, cleared on fork/init or explicit refresh

### Decision 2: Schemas section in Settings panel using JBList

Add a "Schemas" titled border section to `OpenSpecSettingsPanel` below the existing sections. Uses `JBList` to display schemas with a toolbar strip (Fork, New, Refresh buttons).

**Why over alternative (separate Configurable page):** Schema management is a small concern — 3-5 items typically. It fits naturally in the existing Settings page alongside CLI path and profile. A separate page would over-fragment the settings.

**Implementation:**
- New private method `buildSchemasSection()` in `OpenSpecSettingsPanel`
- `JBList<SchemaInfo>` with custom cell renderer showing name + description
- `JComboBox<String>` for default schema selector (populated from schema list)
- "Fork" button: enabled when a schema is selected, opens fork dialog, then `FileEditorManager.openFile()`
- "New" button: opens `NewSchemaDialog`
- "Refresh" button: clears cache and reloads
- Default schema stored in `OpenSpecSettings.State.defaultSchema`

### Decision 3: NewSchemaDialog with artifact checkboxes

A `DialogWrapper` subclass with three fields: name (text), description (text area), and artifacts (checkbox list). Delegates to `SchemaService.initSchema()`.

**Why over alternative (CLI interactive mode):** The CLI's interactive mode cannot be driven from IntelliJ's process handler. A dialog captures the same inputs and passes them as CLI arguments.

**Implementation:**
- `NewSchemaDialog` extends `DialogWrapper`
- Fields: `JBTextField` for name, `JBTextArea` for description, `CheckBoxList` for artifact types (proposal, design, specs, tasks, plus custom entry)
- Validation: name required, must be kebab-case
- On OK: calls `SchemaService.initSchema(name)` then opens the schema file in editor

### Decision 4: Conditional schema dropdown in ProposeChangeDialog and FfDialog

When the project has multiple schemas (more than one), a schema combo box appears in both dialogs. When only one schema exists (the default case), the combo box is hidden to keep the UI clean.

**Why over alternative (always show):** Most users will have a single schema. Showing an always-present dropdown adds visual noise for no benefit. The conditional approach keeps the common case simple.

**Implementation:**
- Both dialogs query `SchemaService.listSchemas()` in their constructor
- If `schemas.size() > 1`: add `JComboBox<String>` populated with schema names, pre-selected to `OpenSpecSettings.defaultSchema`
- The selected schema is passed to `openspec new change "<name>" --schema "<schema>"` via `CliRunner`
- `ProposeChangeDialog.getSelectedSchema()` and `FfDialog.getSelectedSchema()` return the selection (or null if combo hidden)

### Decision 5: CLI version guard for experimental commands

The `schema` commands are experimental. Guard calls with a CLI version check and provide a clear error message if the CLI does not support them.

**Why over alternative (no guard):** If a user has an older CLI that doesn't support schema commands, the error message from `CliRunner` would be cryptic. A version guard gives a clear "OpenSpec CLI v1.x+ required for schema management" message.

**Implementation:**
- `SchemaService.isSchemaSupported()` checks `CliDetectionService.getDetectedVersion()` against minimum version
- If unsupported: Settings panel shows "Schema management requires OpenSpec CLI v1.2.0+" label instead of schema list
- Fork/New/dropdown features are disabled gracefully

## Risks / Trade-offs

- **[Risk] CLI schema commands are experimental** — API may change between CLI releases. Mitigation: `SchemaService` isolates all CLI interaction; if the JSON format changes, only the service needs updating. Version guard prevents crashes on older CLIs.
- **[Risk] Schema list may be slow on first load** — `openspec schemas --json` spawns a CLI process. Mitigation: caching within the settings panel session; async loading with a "Loading..." placeholder in the JBList.
- **[Trade-off] No schema editing UI** — Users must edit schema YAML in the standard editor after forking. This is acceptable because schema editing is infrequent and the YAML format is simple. Adding a custom editor would be high effort for low usage.
- **[Trade-off] Conditional combo box adds branching in dialog code** — Two code paths (with/without schema selector) in ProposeChangeDialog and FfDialog. Mitigated by extracting the schema combo logic into a shared utility method.
