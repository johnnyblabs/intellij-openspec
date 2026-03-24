## Why

The `VersionSupport` enum defines per-version differences (required config fields, required artifacts, valid schemas), but `BuiltInValidator` only partially uses this information. The `version` field in `config.yaml` is never validated, `requiredConfigFields` is defined but never checked, and each change's `.openspec.yaml` schema is never validated against the project's declared version. This allows silent mismatches — a 1.0.0 project could declare `schema: tdd` (only valid from 1.1.0) with no warning.

## What Changes

### config.yaml validation
- Validate that `version` field is present (error if missing)
- Validate that the `version` value is a recognized OpenSpec version (error if unknown)
- Enforce required config fields for the declared version using `VersionSupport.getRequiredConfigFields()`

### .openspec.yaml validation
- Validate that each change's `schema` field is valid for the project's declared version
- Warn if a change uses a schema not supported by the project version (e.g., `tdd` on a 1.0.0 project)

### Cross-validation
- Warn if a change references artifacts not defined for the project's declared version

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `validation`: Extend config validation to enforce version field presence, version format recognition, required config fields per version, and schema-version compatibility for both `config.yaml` and `.openspec.yaml`

## Impact

- `BuiltInValidator.java` — `validateConfig()` and `validateSingleChange()` extended
- `VersionSupport.java` — `requiredConfigFields` already defined, now actually used
- No new dependencies, no API changes
