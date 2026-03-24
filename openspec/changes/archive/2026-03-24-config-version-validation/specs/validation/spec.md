## MODIFIED Requirements

### Requirement: Config validation

The plugin SHALL validate `config.yaml` for structural correctness, required fields, version presence, version recognition, and schema-version compatibility. Validation SHALL use the `VersionSupport` enum to determine which fields and schemas are valid for the declared version.

#### Scenario: Config parsed successfully
- **WHEN** `config.yaml` exists and is valid YAML
- **THEN** the validator SHALL proceed with field-level checks

#### Scenario: Config missing
- **WHEN** `config.yaml` does not exist or cannot be parsed
- **THEN** the validator SHALL report an ERROR with rule `config-missing`

#### Scenario: Schema field required
- **WHEN** `config.yaml` has no `schema` field
- **THEN** the validator SHALL report an ERROR with rule `config-schema-required`

#### Scenario: Schema value invalid for version
- **WHEN** the `schema` value is not in the declared version's valid schemas
- **THEN** the validator SHALL report a WARNING with rule `config-schema-invalid`

#### Scenario: Version field missing
- **WHEN** `config.yaml` has no `version` field
- **THEN** the validator SHALL report a WARNING with rule `config-version-required`

#### Scenario: Version value unrecognized
- **WHEN** the `version` value does not match any known OpenSpec version
- **THEN** the validator SHALL report a WARNING with rule `config-version-unknown`

#### Scenario: Required config fields enforced
- **WHEN** the declared version requires specific config fields (per `VersionSupport.getRequiredConfigFields()`)
- **THEN** the validator SHALL report an ERROR with rule `config-field-required` for each missing required field

#### Scenario: Profile field recommended
- **WHEN** `config.yaml` has no `profile` field
- **THEN** the validator SHALL report a WARNING with rule `config-profile-recommended`

### Requirement: Change validation

The plugin SHALL validate each active change's `.openspec.yaml` for schema compatibility with the project's declared version. The validator SHALL use `VersionSupport.getValidSchemas()` to determine allowed schemas.

#### Scenario: Proposal required
- **WHEN** a change has no `proposal.md`
- **THEN** the validator SHALL report an ERROR with rule `change-proposal-required`

#### Scenario: Required artifacts for version
- **WHEN** the declared version requires specific artifacts and a change is missing one
- **THEN** the validator SHALL report a WARNING (or ERROR in strict mode) with rule `change-artifact-missing`

#### Scenario: Change schema incompatible with project version
- **WHEN** a change's `.openspec.yaml` declares a schema not in the project version's valid schemas
- **THEN** the validator SHALL report a WARNING with rule `change-schema-incompatible`
