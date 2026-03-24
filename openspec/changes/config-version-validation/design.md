## Context

`VersionSupport` already defines per-version metadata:

| Version | Required Config Fields | Required Artifacts | Valid Schemas |
|---|---|---|---|
| 1.0.0 | schema | proposal | spec-driven |
| 1.1.0 | schema, version | proposal, design | spec-driven, tdd |
| 1.2.0 | schema, version | proposal, design, specs, tasks | spec-driven, tdd, rapid |

`validateConfig()` currently checks:
- config.yaml exists and parses
- `schema` field is present
- schema value is in `version.getValidSchemas()`
- `profile` field is present (warning)

`validateSingleChange()` currently checks:
- proposal.md exists
- required artifacts for the version exist
- delta spec structure

Neither validates the version field itself, enforces `requiredConfigFields`, or cross-validates `.openspec.yaml` schema against the project version.

## Goals / Non-Goals

**Goals:**
- Validate `version` field in config.yaml (present, recognized)
- Enforce `requiredConfigFields` for the declared version
- Validate `.openspec.yaml` schema against project version's valid schemas
- Add new rule IDs for each check so errors are specific and actionable

**Non-Goals:**
- Auto-migrating configs between versions
- Supporting custom/unknown versions (they default to latest)
- Changing `VersionSupport` enum structure

## Decisions

**Add validation to existing methods, don't create new validators.**

Extend `validateConfig()` with two new checks:
1. `config-version-required` — ERROR if `version` field is missing
2. `config-version-unknown` — WARNING if version is not in `VersionSupport.allVersions()` (default-to-latest still works, but user should know)
3. Use `requiredConfigFields` to check all required fields are present — `config-field-required` ERROR per missing field

Extend `validateSingleChange()` with one new check:
4. `change-schema-incompatible` — WARNING if the change's `.openspec.yaml` schema is not in `version.getValidSchemas()`

*Alternative considered:* Making unknown version an ERROR — rejected because the plugin defaults to latest for forward compatibility. A warning is sufficient.

*Alternative considered:* Validating `.openspec.yaml` format (YAML syntax, required fields) — could be added but the CLI already handles creation. Keep scope focused on version compatibility.

## Risks / Trade-offs

**[Risk] Existing projects with missing version field get new errors** → The version field has been part of the spec since 1.1.0, and the CLI always generates it. Projects without it are likely 1.0.0 era. Use WARNING for missing version (not ERROR) to avoid breaking existing workflows, and default to latest behavior as today.

**[Risk] False positives on custom schemas** → The `config-schema-invalid` rule already handles this as a WARNING. The new `change-schema-incompatible` should also be WARNING for consistency.
