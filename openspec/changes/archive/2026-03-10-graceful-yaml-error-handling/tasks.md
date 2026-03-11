## 1. Enhance ConfigService error handling

- [x] 1.1 In `ConfigService.reload()`, catch `MarkedYAMLException` specifically and extract line, column, and problem description
- [x] 1.2 Show `OpenSpecNotifier.warn()` with file path and error location (e.g., "config.yaml: line 5, column 3: mapping values are not allowed here")
- [x] 1.3 Keep the generic `Exception` catch as a fallback for non-YAML errors (IO, etc.)
- [x] 1.4 Log at WARN level (not ERROR) — malformed config is recoverable

## 2. Enhance ChangeService error handling

- [x] 2.1 In `ChangeService.getChangesFromDir()`, catch `MarkedYAMLException` specifically when loading `.openspec.yaml`
- [x] 2.2 Show `OpenSpecNotifier.warn()` with the file path and parse error description
- [x] 2.3 Ensure the change still appears in the tree even when metadata parsing fails

## 3. Harden TrackingMetadataWriter

- [x] 3.1 In `TrackingMetadataWriter.readYaml()`, replace `new Yaml()` with `new Yaml(new LoaderOptions())` for safe loading

## 4. Add YAML syntax validation to ConfigValidationInspection

- [x] 4.1 In `ConfigValidationInspection`, add YAML parse check: attempt `new Yaml().load(text)` and catch `MarkedYAMLException`
- [x] 4.2 Register a `ProblemDescriptor` at the error offset (convert line/column from the exception to text offset)
- [x] 4.3 Apply the inspection to `.openspec.yaml` files in addition to `config.yaml`

## 5. Add tests

- [x] 5.1 Add a test for `ConfigService` with malformed YAML input — verify it doesn't throw and returns null config
- [x] 5.2 Add a test fixture with intentionally malformed YAML (bad indentation, unclosed quote)

## 6. Verify

- [x] 6.1 Run `./gradlew clean build test` — all green
