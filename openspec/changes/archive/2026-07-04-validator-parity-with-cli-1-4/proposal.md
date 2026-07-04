# Validator parity with OpenSpec CLI 1.4

## Why

OpenSpec CLI 1.4.0 made two permanent parser/validator improvements the plugin has not mirrored: requirement headers now parse **case-insensitively**, and `openspec validate` emits a **targeted hint** ("move the keyword onto the requirement body line") when a requirement carries its RFC 2119 keyword only in the header. The plugin's built-in validator, inspections, and every other surface that recognizes `### Requirement:` headers match case-sensitively — so the plugin flags (or silently omits) specs the CLI accepts. This is the last durable 1.4-line feature gap identified by the per-CLI-version feature-delta analysis (`docs/cli-versions/1.4.md`, decision 2); unlike the 1.4 coordination beta, these behaviors persist in CLI 1.5+.

## What Changes

- **Case-insensitive requirement-header recognition everywhere the plugin parses headers.** `### Requirement:` / `### requirement:` / `### REQUIREMENT:` are all recognized, matching the CLI. Affected matchers: the built-in validator, the spec-format and delta-spec inspections, spec parsing for the tree view, and delta→main spec sync matching (RENAMED/MODIFIED/REMOVED lookups find a requirement regardless of its header casing).
- **Keyword-placement hint with quick-fix.** When a requirement's RFC 2119 keyword appears only in the header line and not in the requirement body, the validator/inspection reports the CLI's targeted guidance ("move the keyword to the requirement body") instead of a generic missing-keyword error, and the inspection offers a quick-fix that is safe and mechanical.
- No new configuration; behavior aligns with what the CLI already accepts, so previously-valid specs stay valid.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `validation`: requirement-header recognition becomes case-insensitive (matching CLI 1.4+); a new keyword-in-header-only condition produces a targeted "move keyword to body" diagnostic with quick-fix instead of the generic `spec-rfc-keywords` error.
- `spec-sync`: requirement-block matching during delta→main sync becomes case-insensitive so sync resolves the same requirement blocks the CLI parser does.

## Impact

- **Code:** `validation/BuiltInValidator.java`, `validation/DeltaSpecInspection.java`, `validation/SpecFormatInspection.java`, `services/SpecParsingService.java`, `services/SpecSyncService.java` (all currently use case-sensitive `### Requirement:` patterns); secondary header consumers (`SpecAnnotator`, tree/line-marker surfaces) audited for the same pattern.
- **Tests:** pattern-level unit tests plus fixture-driven cases for lowercase/mixed-case headers; the keyword-in-header-only hint gets both validator and inspection coverage. No external-output parsing is added, so no new CLI contract fixtures are required.
- **Docs:** `docs/openspec-support.md` rows for the two 1.4.0 items move from Partial to supported; `docs/cli-versions/1.4.md` delta table updated.
- **Compatibility:** no platform-API surface touched; IntelliJ 2024.2+ support unaffected. No breaking change — strictly widens what the plugin accepts and improves one diagnostic message.
