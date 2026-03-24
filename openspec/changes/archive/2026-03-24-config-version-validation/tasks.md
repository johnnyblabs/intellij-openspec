## 1. Config Version Validation

- [x] 1.1 Add version field presence check to `validateConfig()` — WARNING with rule `config-version-required` if missing
- [x] 1.2 Add version value recognition check — WARNING with rule `config-version-unknown` if not in `VersionSupport.allVersions()`
- [x] 1.3 Enforce `requiredConfigFields` for declared version — ERROR with rule `config-field-required` per missing field

## 2. Change Schema Cross-Validation

- [x] 2.1 Read `.openspec.yaml` schema in `validateSingleChange()` and check against `version.getValidSchemas()`
- [x] 2.2 Report WARNING with rule `change-schema-incompatible` if schema is not valid for project version

## 3. Testing

- [x] 3.1 Test: missing version field produces `config-version-required` WARNING
- [x] 3.2 Test: unrecognized version produces `config-version-unknown` WARNING
- [x] 3.3 Test: missing required config field produces `config-field-required` ERROR
- [x] 3.4 Test: change with incompatible schema produces `change-schema-incompatible` WARNING
- [x] 3.5 Test: valid config with all fields passes without new issues
- [x] 3.6 Test: 1.0.0 project with `spec-driven` schema passes, `tdd` schema warns
