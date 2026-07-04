# Complete the schema tooling surface

## Why

The plugin's schema management (Settings → schema list, fork, create) covers schema *creation* but not schema *verification or authoring*: a user can fork or init a custom workflow schema and then has no IDE way to check it is well-formed (`schema validate`), see where a schema name actually resolves from when project/user/package copies shadow each other (`schema which`), or open the schema's templates for editing (`templates --json`). All three CLI commands are present on the current 1.4.1 CLI and — unlike the 1.4 coordination beta — the schema system is documented upstream as the standard workflow-customization mechanism and survives into CLI 1.5+, so this investment does not expire. Identified as one of four durable, non-expiring CLI coverage gaps by the 1.4.x release-scoping audit.

## What Changes

- **Validate schema action.** For a selected schema (or after fork/init), the plugin runs `openspec schema validate <name> --json` and presents the result — structure errors, missing templates, circular dependencies — in the schema management UI instead of leaving authors to a terminal round-trip.
- **Resolution provenance.** The schema list/details surface where each schema resolves from (project `openspec/schemas/` vs user vs package built-in) via `openspec schema which <name> --json`, so shadowing is visible instead of surprising.
- **Open templates for editing.** A schema's artifact templates are resolved via `openspec templates --schema <name> --json` and opened in the editor, giving custom-schema authors a direct edit loop (upstream: "change a template, test immediately, no rebuild").
- All three actions are CLI-delegated with the existing graceful degradation when the CLI is unavailable or below the floor (actions hidden/disabled with the standard guidance, matching the current fork/init behavior).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `schema-management`: adds schema validation, resolution provenance, and template-editing requirements to the existing listing/fork/create surface.

## Impact

- **Code:** `services/SchemaService.java` (three new CLI delegations + JSON parsing), `settings/OpenSpecSettingsPanel.java` schema section (validate action, provenance display, open-templates action). CLI calls run off the EDT like existing schema operations.
- **Tests:** contract tests against captured real CLI output for `schema validate --json`, `schema which --json`, and `templates --json` fixtures (per the contract-testing rule — no hand-authored shapes); service unit tests for parse/degradation paths; UI-level test for action enablement gating.
- **Docs:** `docs/openspec-support.md` rows for `templates` / `schema which` / `schema validate` move from "not surfaced" to supported; feature reference updated.
- **Compatibility:** no platform-API additions; IntelliJ 2024.2+ unaffected. The new actions sit behind the existing schema-management CLI version guard (`SchemaService.MIN_CLI_VERSION`); the three commands are confirmed on the current 1.4.1 CLI, and their exact availability on a 1.3.x CLI is verified during implementation (degradation to hidden/disabled follows the existing pattern either way).
