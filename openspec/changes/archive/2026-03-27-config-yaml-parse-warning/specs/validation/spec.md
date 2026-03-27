## MODIFIED Requirements

### Requirement: Config validation

The plugin SHALL validate `config.yaml` for structural correctness, required fields, version presence, version recognition, and schema-version compatibility. Validation SHALL use the `VersionSupport` enum to determine which fields and schemas are valid for the declared version. Config parsing SHALL be lenient — unrecognized fields and type mismatches SHALL be silently ignored rather than raising parse errors.

#### Scenario: Config parsed successfully
- **WHEN** `config.yaml` exists and is valid YAML
- **THEN** the validator SHALL proceed with field-level checks

#### Scenario: Config with unknown fields parsed successfully
- **WHEN** `config.yaml` contains fields not recognized by the Java model
- **THEN** the parser SHALL ignore unknown fields and extract known fields normally

#### Scenario: Config with type mismatches parsed leniently
- **WHEN** a `config.yaml` field has a YAML type that does not match the expected Java type (e.g., array where string expected)
- **THEN** the parser SHALL skip the mismatched field and use its default value

#### Scenario: Config parse failure returns defaults
- **WHEN** `config.yaml` is malformed YAML that cannot be parsed at all
- **THEN** the parser SHALL return a default empty config and log at debug level without showing an IDE notification

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
