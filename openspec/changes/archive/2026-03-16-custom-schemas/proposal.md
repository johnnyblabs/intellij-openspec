## Why

The OpenSpec CLI supports custom workflow schemas — allowing teams to define their own artifact pipelines with different DAGs, artifact types, and rules. The plugin currently hardcodes the `spec-driven` schema and provides no way to list, select, fork, or create schemas from the IDE. Users who need non-standard pipelines (e.g., TDD-first, rapid prototyping, compliance-heavy) must drop to the terminal to manage schemas, then manually configure their project. Adding schema management to the plugin completes the v0.3.0 "Ecosystem" milestone and removes the last CLI-only workflow gap for schema operations.

## What Changes

- New `SchemaService` that wraps `openspec schemas --json`, `openspec schema fork`, and `openspec schema init` CLI commands with structured result parsing
- New "Schemas" section in the OpenSpec Settings panel showing available schemas, a default schema selector, and Fork/New buttons
- "Fork" button that delegates to `openspec schema fork` and opens the forked schema file in the editor
- "New Schema" dialog (`NewSchemaDialog`) with name, description, and artifact selection fields that delegates to `openspec schema init`
- Schema dropdown added to `ProposeChangeDialog` and `FfDialog`, visible only when the project has multiple schemas available
- Tests covering SchemaService CLI interactions, settings panel integration, and dialog behavior

## Capabilities

### New Capabilities
- `custom-schemas`: Custom workflow schema management — list, select, fork, and create schemas from the IDE Settings panel and change-creation dialogs

### Modified Capabilities
- `workflow`: Add schema selector to ProposeChangeDialog and FfDialog when multiple schemas are available
- `plugin-core`: Add default schema preference to OpenSpecSettings persistent state

## Impact

- **Services**: New `SchemaService` (`@Service(Service.Level.PROJECT)`) wrapping 3 CLI commands with JSON parsing
- **Settings**: New `defaultSchema` field in `OpenSpecSettings.State`; new "Schemas" section in `OpenSpecSettingsPanel` with JBList, Fork button, and New button
- **Dialogs**: New `NewSchemaDialog` (extends `DialogWrapper`); modified `ProposeChangeDialog` and `FfDialog` with conditional schema combo box
- **CLI integration**: New CLI commands: `openspec schemas --json`, `openspec schema fork <source> <name>`, `openspec schema init <name>`
- **plugin.xml**: No new actions required (schema management is in Settings, not action-based)
- **Dependencies**: No new external dependencies — builds on existing `CliRunner`, `DialogWrapper`, and `PersistentStateComponent` patterns
