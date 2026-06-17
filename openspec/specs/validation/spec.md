# Validation

## Purpose
Built-in validation of OpenSpec project structure, spec format, config integrity, delta spec correctness, and artifact completeness — with real-time IDE inspections and file watching.

## Requirements

### Requirement: Config validation

The plugin SHALL validate `config.yaml` for structural correctness, required fields, version presence, version recognition, and schema-version compatibility. Validation SHALL use the `VersionSupport` enum to determine which fields are valid for the declared version. The supported `VersionSupport` baseline is V1_2 (config-format version 1.2.0) and later; legacy config-format versions 1.0.0 and 1.1.0 SHALL be routed to V1_2 baseline for validation purposes. Schema-name recognition SHALL be CLI-runtime-driven: the validator SHALL consider a schema name "recognized" when it appears in the union of (a) the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) and (b) the CLI-runtime set from `SchemaService.listSchemas()` when CLI is available and supports schema management. Config parsing SHALL be lenient — unrecognized fields and type mismatches SHALL be silently ignored rather than raising parse errors. The config validation inspection SHALL guard against zero-length PSI elements before creating problem descriptors.

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
- **WHEN** the `config.yaml` `schema` value is neither in the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) nor in the CLI-runtime set from `SchemaService.listSchemas()`
- **THEN** the validator SHALL report a WARNING with rule `config-schema-invalid`. The warning text SHALL list the known-set and indicate CLI status (available / unavailable / below floor) so the user understands why a custom-forked schema might not be visible.

#### Scenario: Built-in schema accepted regardless of CLI state
- **WHEN** the `config.yaml` `schema` value is one of the built-in fallback set (currently `"spec-driven"` or `"workspace-planning"`)
- **THEN** the validator SHALL NOT report `config-schema-invalid` regardless of whether the CLI is available

#### Scenario: Custom-forked schema accepted when CLI lists it
- **WHEN** the CLI is available and supports schema management, AND the user has run `openspec schema fork spec-driven my-team-flow` so `SchemaService.listSchemas()` returns a `SchemaInfo` with name `"my-team-flow"`, AND a `config.yaml` declares `schema: my-team-flow`
- **THEN** the validator SHALL NOT report `config-schema-invalid`

#### Scenario: Custom-forked schema with no CLI falls back to built-ins
- **WHEN** the CLI is unavailable (or below the 1.3.0 floor) AND a `config.yaml` declares a non-built-in schema like `my-team-flow`
- **THEN** the validator SHALL report `config-schema-invalid` with text indicating the CLI is unavailable so custom schemas can't be verified

#### Scenario: Typo schema name continues to warn
- **WHEN** the `config.yaml` `schema` value is a typo of a built-in (e.g., `"spec-drivenn"`) — not in built-ins AND not in the CLI-runtime list
- **THEN** the validator SHALL report `config-schema-invalid` — the broadened recognition does not mask typos

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

#### Scenario: Empty PSI element during config inspection
- **WHEN** `findElementAt()` or `getFirstChild()` returns a zero-length PSI element during config validation
- **THEN** the inspection SHALL walk up via `getParent()` to find a non-empty ancestor and use it for the problem descriptor

#### Scenario: Legacy config-format version 1.0.0 routes to V1_2 baseline
- **WHEN** a `config.yaml` declares `version: 1.0.0` (the legacy V1_0 baseline, removed from `VersionSupport`)
- **THEN** `VersionSupport.fromString("1.0.0")` SHALL return `V1_2`, and the validator SHALL apply V1_2's required-field and required-artifact rules. No crash; no NPE on a missing enum value.

#### Scenario: Legacy config-format version 1.1.0 routes to V1_2 baseline
- **WHEN** a `config.yaml` declares `version: 1.1.0` (the legacy V1_1 baseline, removed from `VersionSupport`)
- **THEN** `VersionSupport.fromString("1.1.0")` SHALL return `V1_2`, and the validator SHALL apply V1_2's rules

#### Scenario: Current and newer config-format versions resolve correctly
- **WHEN** a `config.yaml` declares `version: 1.2.0`, `1.2.99`, `1.3.0`, or `1.4.x`
- **THEN** `VersionSupport.fromString` SHALL return `V1_2` for all (V1_2 is the only baseline; future format versions will add their own enum entries when they change the config-format shape)

### Requirement: Spec format validation

The plugin SHALL validate spec files for structural completeness: title heading, requirement blocks, RFC 2119 keywords, and scenario format. The inspection SHALL guard against zero-length PSI elements and invalid offsets before creating problem descriptors.

#### Scenario: Missing title heading
- **WHEN** a spec file has no `# Title` heading
- **THEN** the validator SHALL report an ERROR with code `spec-title-required`

#### Scenario: Missing requirement block
- **WHEN** a spec file has no `### Requirement:` section
- **THEN** the validator SHALL report an ERROR with code `spec-requirement-required`

#### Scenario: Missing RFC 2119 keywords
- **WHEN** a requirement block contains no RFC 2119 keywords (SHALL, SHOULD, MAY, SHALL NOT, SHOULD NOT)
- **THEN** the validator SHALL report an ERROR with code `spec-rfc-keywords`

#### Scenario: Missing scenario
- **WHEN** a requirement block has no `#### Scenario:` section
- **THEN** the validator SHALL report an ERROR with code `spec-scenario-required`

#### Scenario: Scenario missing clauses
- **WHEN** a scenario block is missing a WHEN clause, a THEN clause, or both
- **THEN** the validator SHALL report an ERROR with code `spec-scenario-clauses`

#### Scenario: Invalid offset from text search
- **WHEN** `String.indexOf()` returns `-1` while locating a requirement heading
- **THEN** the inspection SHALL skip that requirement rather than passing an invalid offset to `findElementAt()`

### Requirement: Change validation

The plugin SHALL validate each active change's `.openspec.yaml` for schema compatibility with the project's declared version. Schema-name recognition SHALL be CLI-runtime-driven on the same principle as `Config validation`: a change schema is "recognized" when it appears in the union of the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) and the CLI-runtime set from `SchemaService.listSchemas()`. The validator SHALL use `VersionSupport.getValidSchemas()` only as the built-in fallback portion of that union, not as the canonical valid-set.

#### Scenario: Proposal required
- **WHEN** a change has no `proposal.md`
- **THEN** the validator SHALL report an ERROR with rule `change-proposal-required`

#### Scenario: Required artifacts for version
- **WHEN** the declared version requires specific artifacts and a change is missing one
- **THEN** the validator SHALL report a WARNING (or ERROR in strict mode) with rule `change-artifact-missing`

#### Scenario: Change schema incompatible with project version
- **WHEN** a change's `.openspec.yaml` declares a schema that is neither in the built-in fallback set nor in `SchemaService.listSchemas()`
- **THEN** the validator SHALL report a WARNING with rule `change-schema-incompatible`. The warning text SHALL list the known-set and indicate CLI status so the user understands why a custom-forked schema might not be visible.

#### Scenario: Custom-forked schema accepted for change when CLI lists it
- **WHEN** the user has forked a custom schema via `openspec schema fork` AND a change's `.openspec.yaml` declares that custom schema
- **THEN** the validator SHALL NOT report `change-schema-incompatible`

### Requirement: workspace-planning schema acceptance

The plugin SHALL accept `workspace-planning` as a valid workflow schema for projects on schema-version baseline V1_2 or later, alongside the original `spec-driven` schema. Projects on V1_0 or V1_1 SHALL continue to accept only `spec-driven`, because `workspace-planning` was introduced upstream in OpenSpec CLI 1.4.x.

#### Scenario: workspace-planning accepted under V1_2
- **WHEN** a project declares `version: 1.2.x` (or any version that resolves to `VersionSupport.V1_2`) and a change's `.openspec.yaml` declares `schema: workspace-planning`
- **THEN** the validator SHALL NOT report `change-schema-incompatible`

#### Scenario: spec-driven still accepted under V1_2
- **WHEN** a project declares `version: 1.2.x` and a change's `.openspec.yaml` declares `schema: spec-driven`
- **THEN** the validator SHALL NOT report `change-schema-incompatible`

#### Scenario: workspace-planning rejected under V1_0
- **WHEN** a project declares `version: 1.0.x` (resolving to `VersionSupport.V1_0`) and a change's `.openspec.yaml` declares `schema: workspace-planning`
- **THEN** the validator SHALL report a WARNING with rule `change-schema-incompatible`

#### Scenario: workspace-planning rejected under V1_1
- **WHEN** a project declares `version: 1.1.x` (resolving to `VersionSupport.V1_1`) and a change's `.openspec.yaml` declares `schema: workspace-planning`
- **THEN** the validator SHALL report a WARNING with rule `change-schema-incompatible`

### Requirement: Delta spec validation

The plugin SHALL validate delta spec files for structural correctness, including section headings (ADDED, MODIFIED, REMOVED, RENAMED), removal metadata, scenario coverage, and rename FROM/TO structure.

#### Scenario: Missing delta sections
- **WHEN** a delta spec file under a change's `specs/` directory has no ADDED, MODIFIED, REMOVED, or RENAMED sections
- **THEN** the validator SHALL report a WARNING with code `delta-spec-sections`

#### Scenario: Removed requirement missing metadata
- **WHEN** a REMOVED requirement block is missing a **Reason** field or a **Migration** field
- **THEN** the validator SHALL report an ERROR with code `delta-removed-fields`

#### Scenario: Added requirement missing scenario
- **WHEN** an ADDED requirement block has no `#### Scenario:` section
- **THEN** the validator SHALL report an ERROR with code `delta-requirement-scenario`

#### Scenario: Modified requirement missing scenario
- **WHEN** a MODIFIED requirement block has no `#### Scenario:` section with updated content
- **THEN** the validator SHALL report an ERROR with code `delta-requirement-scenario`

#### Scenario: Renamed section missing FROM/TO
- **WHEN** a `## RENAMED Requirements` section contains no well-formed `FROM:`/`TO:` pair (matching `^\s*(?:-\s*)?FROM:\s*(.+)$\s*^\s*(?:-\s*)?TO:\s*(.+)$`, mirroring the sync layer's parser)
- **THEN** the validator SHALL report an ERROR with code `delta-renamed-fields`

#### Scenario: Renamed section with valid FROM/TO
- **WHEN** a `## RENAMED Requirements` section contains one or more well-formed `FROM:`/`TO:` pairs (bullet or non-bullet form)
- **THEN** the validator SHALL NOT report `delta-renamed-fields` or `delta-spec-sections` for that file

### Requirement: Delta spec IDE inspection

The plugin SHALL provide a real-time IDE inspection for delta spec files, highlighting structural problems as the user edits. For MODIFIED requirements missing scenarios, the inspection SHALL offer a quick-fix that copies the full requirement block from the corresponding main spec. The inspection SHALL guard against zero-length PSI elements by walking up the PSI tree before creating problem descriptors.

#### Scenario: Inspection scope
- **WHEN** a file is located under `openspec/changes/<change>/specs/` and is named `spec.md`
- **THEN** the plugin SHALL apply delta spec inspections to that file

#### Scenario: Inspection highlights errors inline
- **WHEN** a delta spec file has a REMOVED requirement missing metadata or an ADDED requirement missing a scenario
- **THEN** the IDE SHALL display inline error highlights at the relevant locations

#### Scenario: Quick-fix for MODIFIED requirement missing scenarios
- **WHEN** a MODIFIED requirement has no `#### Scenario:` section and the requirement exists in the main spec
- **THEN** the inspection SHALL offer a quick-fix that replaces the requirement block with the full content from `openspec/specs/<capability>/spec.md`

#### Scenario: Quick-fix unavailable when main spec missing
- **WHEN** a MODIFIED requirement has no scenarios but no matching requirement exists in the main spec
- **THEN** the inspection SHALL report the error without offering a quick-fix

#### Scenario: Empty PSI element at inspection offset
- **WHEN** `findElementAt()` returns a zero-length PSI element for a problem location
- **THEN** the inspection SHALL walk up via `getParent()` to find a non-empty ancestor element and use it for the problem descriptor

#### Scenario: No non-empty PSI element available
- **WHEN** no non-empty PSI element can be found at or above the target offset
- **THEN** the inspection SHALL skip registering that problem descriptor rather than crashing

### Requirement: Scaffolding content detection

The plugin SHALL distinguish between scaffolding placeholder content and real authored content, overriding artifact status accordingly.

#### Scenario: Placeholder detection
- **WHEN** an artifact file contains only template placeholders or boilerplate headings with no substantive content
- **THEN** the plugin SHALL report the artifact as scaffolding and override its status to READY (not DONE)

### Requirement: Artifact file watching

The plugin SHALL auto-detect artifact file changes via VFS listeners and refresh pipeline status after clipboard or editor delivery.

#### Scenario: File save triggers refresh
- **WHEN** a user saves an artifact file after pasting AI-generated content
- **THEN** the plugin SHALL detect the VFS change and update the artifact's pipeline status
