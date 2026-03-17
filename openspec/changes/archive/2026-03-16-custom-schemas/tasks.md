## 1. SchemaService and Model

- [x] 1.1 Create `SchemaInfo` record with fields: `name`, `description`, `isBuiltIn`, `artifactIds`
- [x] 1.2 Create `SchemaService` (`@Service(Service.Level.PROJECT)`) with `listSchemas()` calling `openspec schemas --json` via `CliRunner` and parsing JSON into `List<SchemaInfo>`
- [x] 1.3 Add `forkSchema(String source, String name)` method to `SchemaService` calling `openspec schema fork <source> <name>` and returning the forked file path
- [x] 1.4 Add `initSchema(String name)` method to `SchemaService` calling `openspec schema init <name>` and returning the new schema file path
- [x] 1.5 Add schema list caching in `SchemaService` with cache invalidation on fork/init operations
- [x] 1.6 Add `isSchemaSupported()` method that checks CLI version via `CliDetectionService.getDetectedVersion()`
- [x] 1.7 Register `SchemaService` in `plugin.xml`

## 2. Settings Integration

- [x] 2.1 Add `defaultSchema` field to `OpenSpecSettings.State` with getter/setter methods on `OpenSpecSettings`
- [x] 2.2 Add `buildSchemasSection()` method to `OpenSpecSettingsPanel` with titled border panel
- [x] 2.3 Add `JBList<SchemaInfo>` with custom cell renderer showing schema name and description
- [x] 2.4 Add default schema `JComboBox<String>` populated from schema list
- [x] 2.5 Add "Fork" button that calls `SchemaService.forkSchema()` and opens result in `FileEditorManager`
- [x] 2.6 Add "New" button that opens `NewSchemaDialog`
- [x] 2.7 Add "Refresh" button that clears schema cache and reloads the list
- [x] 2.8 Add CLI version guard: show "Requires OpenSpec CLI v1.2.0+" label and disable buttons when unsupported
- [x] 2.9 Wire default schema persistence in `OpenSpecConfigurable.apply()` and `reset()`

## 3. NewSchemaDialog

- [x] 3.1 Create `NewSchemaDialog` extending `DialogWrapper` with name `JBTextField`, description `JBTextArea`, and artifact `CheckBoxList`
- [x] 3.2 Add validation: name required, must match kebab-case pattern
- [x] 3.3 On OK: call `SchemaService.initSchema(name)`, refresh VFS, open schema file in editor
- [x] 3.4 Add public accessors: `getSchemaName()`, `getDescription()`, `getSelectedArtifacts()`

## 4. Dialog Schema Selector

- [x] 4.1 Add schema combo box to `ProposeChangeDialog` (visible when `SchemaService.listSchemas().size() > 1`)
- [x] 4.2 Add `getSelectedSchema()` method to `ProposeChangeDialog` returning selected schema name or null
- [x] 4.3 Add schema combo box to `FfDialog` (visible when multiple schemas exist)
- [x] 4.4 Add `getSelectedSchema()` method to `FfDialog` returning selected schema name or null
- [x] 4.5 Pass `--schema "<schema>"` argument to `openspec new change` in both dialog callers when a schema is selected

## 5. Tests

- [x] 5.1 Add `SchemaServiceTest` with mocked `CliRunner` testing `listSchemas()` JSON parsing
- [x] 5.2 Add test for `forkSchema()` verifying correct CLI arguments and return value
- [x] 5.3 Add test for `initSchema()` verifying correct CLI arguments and return value
- [x] 5.4 Add test for schema list caching and cache invalidation behavior
- [x] 5.5 Add test for `isSchemaSupported()` version checking logic
- [x] 5.6 Add `NewSchemaDialogTest` verifying name validation (blank, non-kebab-case, valid)
- [x] 5.7 Add test for schema combo box visibility logic (hidden with 1 schema, shown with 2+)
