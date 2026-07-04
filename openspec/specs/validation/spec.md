# Validation

## Purpose
Built-in validation of OpenSpec project structure, spec format, config integrity, delta spec correctness, and artifact completeness — with real-time IDE inspections and file watching.

## Requirements

### Requirement: Config validation

The plugin SHALL validate `openspec/config.yaml` for structural correctness when the file exists, applying only the rules that mirror upstream OpenSpec's own contract. The file itself SHALL be optional — matching upstream's `readProjectConfig() → null // No config is OK` semantics — so a project initialized with `openspec init` and never customized SHALL produce zero config-related validation issues. When the file does exist, validation SHALL enforce only the fields upstream defines in its Zod `ProjectConfigSchema` (`schema`, `context`, `rules`); fields the plugin reads as internal-only extensions (`version`, `profile`) SHALL NOT fire required-field issues when absent. Schema-name recognition SHALL be CLI-runtime-driven: the validator SHALL consider a schema name "recognized" when it appears in the union of (a) the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) and (b) the CLI-runtime set from `SchemaService.listSchemas()` when CLI is available and supports schema management. Config parsing SHALL be lenient — unrecognized fields and type mismatches SHALL be silently ignored rather than raising parse errors. The config validation inspection SHALL guard against zero-length PSI elements before creating problem descriptors.

#### Scenario: Config absent is accepted
- **WHEN** `openspec/config.yaml` does not exist
- **THEN** the validator SHALL return a clean `ValidationResult` (passed=true, zero issues). This mirrors upstream's `// No config is OK` behavior — the file is augmentation, not a precondition.

#### Scenario: Config parsed successfully
- **WHEN** `openspec/config.yaml` exists and is valid YAML
- **THEN** the validator SHALL proceed with field-level checks

#### Scenario: Config with unknown fields parsed successfully
- **WHEN** `openspec/config.yaml` contains fields not recognized by the Java model
- **THEN** the parser SHALL ignore unknown fields and extract known fields normally

#### Scenario: Config with type mismatches parsed leniently
- **WHEN** an `openspec/config.yaml` field has a YAML type that does not match the expected Java type (e.g., array where string expected)
- **THEN** the parser SHALL skip the mismatched field and use its default value

#### Scenario: Config parse failure returns defaults
- **WHEN** `openspec/config.yaml` is malformed YAML that cannot be parsed at all
- **THEN** the parser SHALL return a default empty config and log at debug level without showing an IDE notification

#### Scenario: Schema field required when config exists
- **WHEN** `openspec/config.yaml` exists but has no `schema` field (or `schema` is empty)
- **THEN** the validator SHALL report an ERROR with rule `config-schema-required`. This is the only field-presence rule that fires for `openspec/config.yaml`; it mirrors upstream's Zod `schema: z.string().min(1)`.

#### Scenario: Schema value invalid for version
- **WHEN** the `openspec/config.yaml` `schema` value is neither in the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) nor in the CLI-runtime set from `SchemaService.listSchemas()`
- **THEN** the validator SHALL report a WARNING with rule `config-schema-invalid`. The warning text SHALL list the known-set and indicate CLI status (available / unavailable / below floor) so the user understands why a custom-forked schema might not be visible.

#### Scenario: Built-in schema accepted regardless of CLI state
- **WHEN** the `openspec/config.yaml` `schema` value is one of the built-in fallback set (currently `"spec-driven"` or `"workspace-planning"`)
- **THEN** the validator SHALL NOT report `config-schema-invalid` regardless of whether the CLI is available

#### Scenario: Custom-forked schema accepted when CLI lists it
- **WHEN** the CLI is available and supports schema management, AND the user has run `openspec schema fork spec-driven my-team-flow` so `SchemaService.listSchemas()` returns a `SchemaInfo` with name `"my-team-flow"`, AND an `openspec/config.yaml` declares `schema: my-team-flow`
- **THEN** the validator SHALL NOT report `config-schema-invalid`

#### Scenario: Custom-forked schema with no CLI falls back to built-ins
- **WHEN** the CLI is unavailable (or below the 1.3.0 floor) AND an `openspec/config.yaml` declares a non-built-in schema like `my-team-flow`
- **THEN** the validator SHALL report `config-schema-invalid` with text indicating the CLI is unavailable so custom schemas can't be verified

#### Scenario: Typo schema name continues to warn
- **WHEN** the `openspec/config.yaml` `schema` value is a typo of a built-in (e.g., `"spec-drivenn"`) — not in built-ins AND not in the CLI-runtime list
- **THEN** the validator SHALL report `config-schema-invalid` — the broadened recognition does not mask typos

#### Scenario: Version field absent is accepted
- **WHEN** `openspec/config.yaml` has no `version` field (or `version` is empty)
- **THEN** the validator SHALL NOT report any issue. The `version` key is plugin-internal — it is not in upstream's Zod schema and is silently stripped if present at all. `VersionSupport.fromString(null)` defaults to V1_2 so the plugin's runtime behavior is unaffected.

#### Scenario: Version value unrecognized
- **WHEN** the `version` value is set and does not match any known config-format version in `VersionSupport.allVersions()`
- **THEN** the validator SHALL report a WARNING with rule `config-version-unknown`. The check only runs when a value is present; absence is not a warning.

#### Scenario: Required config fields enforced (schema only)
- **WHEN** `openspec/config.yaml` exists and `VersionSupport.V1_2.getRequiredConfigFields()` returns `{schema}` (the only field upstream's Zod requires)
- **THEN** the validator SHALL report an ERROR with rule `config-field-required` only if `schema` is missing. No other fields are enforced as "required" — the plugin matches upstream's stance that `context` and `rules` are optional and `version`/`profile` are plugin-internal extensions.

#### Scenario: Profile field absent is accepted
- **WHEN** `openspec/config.yaml` has no `profile` field (or `profile` is empty)
- **THEN** the validator SHALL NOT report any issue. Upstream does not read `profile`; the plugin uses it only for tree-view display and AI-prompt context, both null-safe.

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

The plugin SHALL validate spec files for structural completeness: title heading, requirement blocks, RFC 2119 keywords, and scenario format. Requirement headers (`### Requirement:`) SHALL be recognized case-insensitively on the header token, matching OpenSpec CLI 1.4+ parsing. RFC 2119 keyword presence SHALL be evaluated against the requirement body; a keyword appearing only in the requirement header SHALL produce a targeted diagnostic directing the author to move the keyword onto a body line, and the inspection SHALL offer a quick-fix for it. The inspection SHALL guard against zero-length PSI elements and invalid offsets before creating problem descriptors.

#### Scenario: Missing title heading
- **WHEN** a spec file has no `# Title` heading
- **THEN** the validator SHALL report an ERROR with code `spec-title-required`

#### Scenario: Missing requirement block
- **WHEN** a spec file has no `### Requirement:` section
- **THEN** the validator SHALL report an ERROR with code `spec-requirement-required`

#### Scenario: Requirement header with non-canonical casing
- **WHEN** a spec file contains a requirement header written as `### requirement:` or `### REQUIREMENT:` (any casing of the header token)
- **THEN** the validator and inspections SHALL recognize it as a requirement block, exactly as the OpenSpec CLI 1.4+ parser does

#### Scenario: Missing RFC 2119 keywords
- **WHEN** a requirement's body contains no RFC 2119 keywords (SHALL, SHOULD, MAY, SHALL NOT, SHOULD NOT) and its header contains none either
- **THEN** the validator SHALL report an ERROR with code `spec-rfc-keywords`

#### Scenario: RFC 2119 keyword only in the header
- **WHEN** a requirement's header line contains an RFC 2119 keyword but its body contains none
- **THEN** the validator SHALL report an ERROR with code `spec-rfc-keyword-in-header` whose message directs the author to move the keyword onto the requirement body line, mirroring the OpenSpec CLI 1.4+ validation hint

#### Scenario: Quick-fix for keyword-in-header
- **WHEN** the inspection reports `spec-rfc-keyword-in-header` on a requirement
- **THEN** it SHALL offer a quick-fix that inserts or rewrites a body line carrying the keyword via a deterministic text edit inside a `WriteAction`, leaving the header text unchanged

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
- **WHEN** a REMOVED requirement block is missing a **Reason** field or a **Migration** field (recognizing both the `**Reason:**` colon-inside and `**Reason**:` colon-outside bold forms)
- **THEN** the validator SHALL report a WARNING with code `delta-removed-fields`
- **AND** the validator SHALL NOT report this as an ERROR, because Reason/Migration are an OpenSpec authoring convention only — the upstream `@fission-ai/openspec` client validates REMOVED blocks by name and does not require these fields, so the plugin must not be stricter than the client it wraps

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

#### Scenario: Inspection highlights problems inline
- **WHEN** a delta spec file has an ADDED requirement missing a scenario
- **THEN** the IDE SHALL display an inline error highlight at the relevant location
- **WHEN** a delta spec file has a REMOVED requirement missing **Reason**/**Migration** metadata
- **THEN** the IDE SHALL display an inline warning highlight (advisory, not an error — see the `delta-removed-fields` rule above)

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
