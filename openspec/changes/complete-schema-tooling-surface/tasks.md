# Tasks — Complete the schema tooling surface

## 1. Fixtures & contract tests (first, per the contract-testing rule)

- [x] 1.1 Capture real CLI output fixtures on 1.4.1 (isolated `XDG_DATA_HOME`, sanitized paths): `schema validate <name> --json` (clean + broken schema), `schema which <name> --json` (built-in + project-forked), `templates --schema <name> --json`; commit under `src/test/resources/fixtures/cli/`
- [x] 1.2 Contract tests parsing the fixtures (extend `CliContractTest` pattern)
- [x] 1.3 Verify on a 1.3.x CLI whether `schema which`, `schema validate`, and `templates` exist; record the finding in `docs/cli-versions/` and encode the per-action gate if any is 1.4+-only

## 2. Service layer

- [x] 2.1 `SchemaService.validateSchema(name)` — run `schema validate <name> --json` off-EDT, parse problems (message + severity), error path returns stderr
- [x] 2.2 `SchemaService.whichSchema(name)` / batch provenance resolution tied to the listing cache lifecycle
- [x] 2.3 `SchemaService.resolveTemplates(name)` — run `templates --schema <name> --json`, return artifact→path map tolerating missing fields
- [x] 2.4 Unit tests for all three including CLI-failure and malformed-output degradation

## 3. Settings UI

- [x] 3.1 Validate action on the selected schema; render problem list with severity inline in the schemas section; post-fork/init follow-up hint
- [x] 3.2 Origin tag (project / user / built-in) on schema list rows, omitted when provenance is unavailable
- [x] 3.3 Open Templates action — open existing files via `FileEditorManager`, skip-and-report missing paths
- [x] 3.4 Action enablement follows the existing CLI version guard (plus any per-action 1.4 gate from 1.3)
- [x] 3.5 UI-level tests for enablement gating and problem rendering

## 4. Documentation

- [x] 4.1 Flip `docs/openspec-support.md` rows for `templates`, `schema which`, `schema validate` from "not surfaced" to supported
- [x] 4.2 Update `docs/feature-reference.md` schema-management section; CHANGELOG entry under Unreleased
