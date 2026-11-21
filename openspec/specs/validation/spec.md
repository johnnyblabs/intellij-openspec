# Validation

## Purpose
Built-in validation of OpenSpec project structure, spec format, config integrity, delta spec correctness, and artifact completeness — with real-time IDE inspections and file watching.

## Requirements

### Requirement: CLI verdict is authoritative when the CLI is available

When the CLI is available and `validate` completes with parseable output, its result SHALL be authoritative for the specs and change deltas it validates: each item's pass/fail SHALL come from its `valid` field. The built-in validator SHALL NOT fail that verdict, and SHALL own `config.yaml` content. When the CLI is unavailable or its run fails, the built-in verdict SHALL apply, never a blind pass.

#### Scenario: Clean CLI is not overridden by the built-in validator
- **WHEN** the CLI is available and reports the project's specs/changes `valid`, but the built-in validator's own spec/change rules would have flagged an ERROR for one of them (any residual stricter-than-CLI rule)
- **THEN** the plugin's whole-project verdict SHALL NOT fail on account of that built-in spec/change ERROR — the CLI verdict is authoritative for specs/changes and the built-in validator's spec/change rules do not contribute to the verdict when the CLI covered them

#### Scenario: Whole-project verdict combines CLI specs/changes with built-in config
- **WHEN** a whole-project validation runs with the CLI available
- **THEN** the merged verdict SHALL be `cli.passed() && builtInConfig.passed()`, and the reported issues SHALL be the union of the CLI's spec/change issues and the built-in validator's `config.yaml` issues

#### Scenario: Config warnings surface but do not fail a clean-CLI verdict
- **WHEN** the CLI reports all specs/changes valid and `openspec/config.yaml` is missing its `schema:` field
- **THEN** the built-in `config-schema-required` WARNING SHALL be surfaced for display alongside the CLI's verdict, and the merged whole-project verdict SHALL pass — config checks are non-failing, so the built-in validator never reds a project the CLI reports clean

#### Scenario: Single-item verdict defers entirely to the CLI
- **WHEN** a single spec or change is validated with the CLI available
- **THEN** the verdict SHALL be the CLI's `valid` result for that item, with no config component merged in

#### Scenario: Failed or unparseable CLI run falls back to the built-in validator
- **WHEN** the CLI is available but the `validate` run throws, exits abnormally, or emits output that is not parseable
- **THEN** the plugin SHALL use the full built-in validator's result as the verdict, not a blind pass

#### Scenario: Verdict parity against captured CLI output
- **WHEN** the captured real CLI `--json` fixture for the default anchor generation (`1.8.0`) is parsed
- **THEN** the plugin's per-item pass/fail SHALL equal each item's `valid` field for every item in the fixture

#### Scenario: Verdict parity is stable across every captured CLI generation
- **WHEN** more than one CLI generation's captured `validate --all --json` over the shared verdict-parity corpus is committed as a fixture
- **THEN** in default mode every captured generation's per-item valid-set SHALL be a subset of the laxest anchor generation's valid-set (currently `1.8.0`), so the plugin — proven to match the anchor — is by transitivity never more restrictive than any captured generation, and any future generation that relaxes a default verdict further than the anchor SHALL fail the guard until the anchor is advanced; under `--strict` every captured generation's valid-set SHALL be a subset of the strict anchor's valid-set (the `1.6.0` map)
- **AND** the guard SHALL surface a vacuous pass as a failure: it SHALL require at least two captured generations including the anchor, SHALL require every captured corpus to carry the identical item-id key set, and SHALL pin the known cross-generation divergence — the `1.8.0` default valid-set SHALL equal the `1.6.0` default valid-set plus exactly the requirements the missing-`SHALL` demotion newly validates — and SHALL likewise pin the known strict divergence — every generation from `1.14.2` on SHALL have a `--strict` valid-set equal to the strict anchor's minus exactly the item whose pre-requirement text archiving would drop, while every earlier captured generation reproduces the strict anchor exactly — so an empty discovery, a classpath regression, a dropped fixture, or a silent collapse or widening of either divergence fails loudly rather than passing on nothing

#### Scenario: Whole-project merge
- **WHEN** a whole-project validation runs with the CLI available
- **THEN** the verdict SHALL be `cli.passed() && builtInConfig.passed()`, and the displayed issues SHALL be the union of the CLI's spec and change issues and the built-in's config issues

#### Scenario: Single target
- **WHEN** a single spec or change is validated with the CLI available
- **THEN** the verdict SHALL be the CLI's alone, with no config component

#### Scenario: Configuration error response
- **WHEN** the CLI answers with a configuration error response
- **THEN** that response SHALL be reported as a failure, not replaced by the built-in verdict (see "Configuration error response is a failed run")

#### Scenario: Durable parity across CLI generations
- **WHEN** the plugin's default verdict is compared with the captured CLI generations
- **THEN** it SHALL match the laxest captured generation (the default anchor, currently 1.8.0), and every generation's default valid-set SHALL be a subset of the anchor's
- **AND** under `--strict`, every generation's valid-set SHALL be a subset of the strict anchor's (the 1.6.0 default map, which `--strict` reproduced on every generation through 1.13)
- **AND** a generation that relaxes a verdict beyond the anchor SHALL surface as a parity failure that prompts advancing the anchor

### Requirement: Config validation

The plugin SHALL validate `openspec/config.yaml` for structural correctness, applying only rules that mirror upstream's contract. Config validation SHALL be display-only and non-failing: it SHALL NOT emit ERROR-severity issues or fail any verdict. The file SHALL be optional, and it SHALL check only the `schema`, `context` and `rules` fields upstream defines.

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
- **THEN** the parser SHALL return a default empty config, SHALL record that the file is unparseable together with the first line of the parser's message, and SHALL log at debug level without showing an IDE notification

#### Scenario: Missing schema is a non-failing warning
- **WHEN** `openspec/config.yaml` exists but has no `schema` field (or `schema` is empty)
- **THEN** the validator SHALL report a single **INFO advisory** with rule `config-schema-required`, SHALL NOT report `config-field-required` for the same field, and the result SHALL pass. It is an advisory, not a WARNING or ERROR — `openspec validate` is clean on a missing schema (it defaults to `spec-driven`), so a warning would be stricter than the CLI.

#### Scenario: Schema value invalid for version
- **WHEN** the CLI is available and supports schema management, AND the `openspec/config.yaml` `schema` value is neither in the built-in fallback set (`VersionSupport.V1_2.getValidSchemas()`) nor in the CLI-runtime set from `SchemaService.listSchemas()`
- **THEN** the validator SHALL report a WARNING with rule `config-schema-invalid`. The warning text SHALL list the known-set and indicate CLI status so the user understands why a custom-forked schema might not be visible. The check runs only when the known-set is authoritative (see the CLI-unavailable scenario).

#### Scenario: Built-in schema accepted regardless of CLI state
- **WHEN** the `openspec/config.yaml` `schema` value is one of the built-in fallback set (currently `"spec-driven"` or `"workspace-planning"`)
- **THEN** the validator SHALL NOT report `config-schema-invalid` regardless of whether the CLI is available

#### Scenario: Custom-forked schema accepted when CLI lists it
- **WHEN** the CLI is available and supports schema management, AND the user has run `openspec schema fork spec-driven my-team-flow` so `SchemaService.listSchemas()` returns a `SchemaInfo` with name `"my-team-flow"`, AND an `openspec/config.yaml` declares `schema: my-team-flow`
- **THEN** the validator SHALL NOT report `config-schema-invalid`

#### Scenario: Custom-forked schema with no CLI falls back to built-ins
- **WHEN** the CLI is unavailable (or below the 1.3.0 floor) AND an `openspec/config.yaml` declares a non-built-in schema like `my-team-flow`
- **THEN** the validator SHALL NOT report `config-schema-invalid` — the known-set is non-authoritative (it collapses to the built-in floor and cannot see project-local forks), so the check is skipped rather than warn on a schema the real CLI accepts. Schema recognition resumes when the CLI is present to supply the authoritative set.

#### Scenario: Typo schema name continues to warn
- **WHEN** the CLI is available and supports schema management, AND the `openspec/config.yaml` `schema` value is a typo of a built-in (e.g., `"spec-drivenn"`) — not in built-ins AND not in the CLI-runtime list
- **THEN** the validator SHALL report `config-schema-invalid` — the authoritative-known-set gate does not mask a genuine typo when the CLI supplies the real set

#### Scenario: Version field absent is accepted
- **WHEN** `openspec/config.yaml` has no `version` field (or `version` is empty)
- **THEN** the validator SHALL NOT report any issue. The `version` key is plugin-internal — it is not in upstream's Zod schema and is silently stripped if present at all. `VersionSupport.fromString(null)` defaults to V1_2 so the plugin's runtime behavior is unaffected.

#### Scenario: Version value unrecognized
- **WHEN** the `version` value is set and does not match any known config-format version in `VersionSupport.allVersions()`
- **THEN** the validator SHALL NOT report any issue. `version` is plugin-internal and the CLI never reads it — `openspec validate` is clean for any value — so warning on an unrecognized value would be stricter than the CLI. `VersionSupport.fromString` routes any value (including legacy `1.0.0`/`1.1.0`) to the V1_2 baseline.

#### Scenario: No required-field enforcement beyond schema
- **WHEN** `openspec/config.yaml` exists and is missing any field other than `schema` (e.g. `context`, `rules`, `version`, `profile`)
- **THEN** the validator SHALL NOT report `config-field-required` for that field. `config-field-required` is no longer emitted; the only required-field rule is `config-schema-required`, covering the sole field upstream's Zod requires. `context` and `rules` are optional and `version`/`profile` are plugin-internal extensions.

#### Scenario: Profile field absent is accepted
- **WHEN** `openspec/config.yaml` has no `profile` field (or `profile` is empty)
- **THEN** the validator AND the config inspection SHALL NOT report any issue. Upstream does not read `profile` (it is the global workflow profile, not a project config field), and the plugin has no reader for it; its absence is never a config issue.

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

#### Scenario: Malformed config is a non-failing warning
- **WHEN** `openspec/config.yaml` exists and is not valid YAML, and the built-in validator owns the verdict (the CLI is absent, or its run fell back)
- **THEN** the validator SHALL report exactly one WARNING with rule `config-unparseable`, located at `config.yaml`, whose message carries the parser's reason, and the result SHALL pass in default mode and SHALL NOT flip under `--strict`

#### Scenario: Unreadable config is a non-failing warning
- **WHEN** `openspec/config.yaml` exists but cannot be read
- **THEN** the validator SHALL report exactly one WARNING with rule `config-unreadable`, located at `config.yaml`, and the result SHALL pass in default mode and SHALL NOT flip under `--strict`

#### Scenario: Non-mapping YAML is not a parse failure
- **WHEN** `openspec/config.yaml` is valid YAML whose top level is a scalar or a list
- **THEN** the validator SHALL NOT report `config-unparseable` or `config-unreadable`

#### Scenario: Unreadable or unparseable file
- **WHEN** the file cannot be read or is not valid YAML and the CLI response does not report it
- **THEN** the built-in check SHALL report a single WARNING (`config-unreadable` or `config-unparseable`) carrying the reason, which SHALL NOT fail the verdict
- **AND** the 1.16 failure SHALL be mirrored only through the CLI's configuration error response

#### Scenario: Non-mapping YAML
- **WHEN** the YAML parses to a scalar or a list
- **THEN** it SHALL NOT be reported as a parse failure, because upstream parses it without error

#### Scenario: Missing schema advisory
- **WHEN** the `schema` field is missing or empty
- **THEN** a single INFO advisory (`config-schema-required`) SHALL be raised, never a WARNING or ERROR

#### Scenario: Schema recognition is authoritative
- **WHEN** a `schema` value is checked
- **THEN** it SHALL be checked only when the CLI is available and supports schema management, so the known set reflects project forks; otherwise the check SHALL be skipped

#### Scenario: Internal and lenient fields
- **WHEN** `version` or `profile` appears, or an unrecognized field or type mismatch appears
- **THEN** no issue SHALL be raised, and parsing SHALL be lenient

#### Scenario: Project initialized and never customized
- **WHEN** a project was initialized with `openspec init` and never customized
- **THEN** validation SHALL produce zero config-related issues

### Requirement: Spec format validation

The plugin SHALL validate main specs for structural completeness: requirement blocks, RFC 2119 keywords and scenario format. Its default verdict SHALL NOT exceed the CLI's for the same file. A requirement with no `#### Scenario:` block and a missing requirement block SHALL remain ERROR. A requirement name that exactly repeats an earlier one in the same file SHALL be an ERROR. Requirement headers SHALL match case-insensitively on the header token.

#### Scenario: Missing title heading warns, does not fail
- **WHEN** a spec file has no `# Title` heading and the built-in validator is the verdict
- **THEN** the validator SHALL report a WARNING with code `spec-title-required`, and the file's verdict SHALL NOT fail on that account, because the CLI requires no H1

#### Scenario: Otherwise-valid spec without a title passes
- **WHEN** a spec file has no `# Title` heading but has a requirement block with a SHALL/MUST body line and at least one scenario
- **THEN** the built-in validator SHALL report the `spec-title-required` WARNING and still return a passing verdict (no ERROR), matching the CLI which validates the same file clean

#### Scenario: Missing requirement block
- **WHEN** a spec file has no `### Requirement:` section
- **THEN** the validator SHALL report an ERROR with code `spec-requirement-required`

#### Scenario: SHOULD-only requirement is flagged
- **WHEN** a requirement's body contains `SHOULD` or `MAY` but neither `SHALL` nor `MUST` as a whole word
- **THEN** the validator SHALL report the missing-keyword rule (`spec-rfc-keywords`) as a **WARNING** in default mode and the file's verdict SHALL NOT fail on that account, matching the 1.8 CLI which reports such a body-carrying requirement `valid` in default

#### Scenario: Keyword only inside a code fence is not accepted
- **WHEN** a requirement's only `SHALL`/`MUST` occurrence sits inside a fenced code block
- **THEN** the validator SHALL report the missing-keyword rule (`spec-rfc-keywords`) as a **WARNING** in default mode (the fenced keyword does not satisfy the check after fence masking, but the requirement has a body, so it is advisory in default), matching CLI 1.6 fence masking and the 1.8 default demotion

#### Scenario: Keyword only in the requirement header is flagged
- **WHEN** a requirement's only `SHALL`/`MUST` occurrence sits in the `### Requirement:` header line and its body has prose without either keyword
- **THEN** the validator SHALL report a targeted `spec-rfc-keyword-in-header` diagnostic as a **WARNING** in default mode, offering a quick-fix to move the keyword onto a body line, and the file's verdict SHALL NOT fail on that account in default, matching the 1.8 CLI

#### Scenario: Requirement with no body prose is an error
- **WHEN** a main-spec requirement has a `#### Scenario:` block but no body prose at all between its `### Requirement:` header and the first scenario
- **THEN** the validator SHALL report the missing-keyword rule (`spec-rfc-keywords`) as an **ERROR**, failing the file's verdict in both default and strict modes, matching the 1.8 CLI which still errors on a body-less requirement

#### Scenario: Requirement without a scenario is an error
- **WHEN** a main-spec requirement (outside a change delta) has a SHALL/MUST body line but no `#### Scenario:` block
- **THEN** the validator SHALL report an ERROR with code `spec-scenario-required`, and the file's verdict SHALL fail, matching the CLI which reports such a requirement `valid:false`

#### Scenario: Scenario header only inside a code fence is an error
- **WHEN** a requirement's only `#### Scenario:` header sits inside a fenced code block (so the requirement is effectively scenarioless after fence masking)
- **THEN** the validator SHALL report the `spec-scenario-required` ERROR, applying CLI 1.6 fence-aware scenario counting

#### Scenario: Scenario missing WHEN or THEN is informational only
- **WHEN** a `#### Scenario:` block is missing a `WHEN` clause, a `THEN` clause, or both
- **THEN** the validator SHALL report an INFO with code `spec-scenario-clauses` and the file's verdict SHALL be unaffected, because the CLI performs no clause-structure validation

#### Scenario: Verdict parity with the 1.6 CLI on demoted main-spec rules
- **WHEN** a main spec that the captured real 1.6.0 CLI reports `valid` (e.g. one that is untitled, or has a clauseless scenario, but no ERROR-class problem) is validated by the built-in validator in default mode
- **THEN** the built-in validator SHALL also report it passing, so the plugin's fallback verdict matches the CLI's `valid` flag for every such case

#### Scenario: Duplicate requirement name in a main spec is an error
- **WHEN** a main spec declares two `### Requirement:` headers with the same name and the built-in validator is the verdict
- **THEN** the validator SHALL report an ERROR with code `spec-duplicate-requirement`, anchored on the second declaration's line and naming the first declaration's line, and the file's verdict SHALL fail, matching the 1.8 CLI which reports such a spec `valid:false`

#### Scenario: A name repeated three times flags the later two
- **WHEN** a main spec declares the same `### Requirement:` name three times
- **THEN** the validator SHALL report exactly two `spec-duplicate-requirement` ERRORs — one on the second occurrence and one on the third — each referencing the first declaration's line, matching the 1.8 CLI which emits N−1 errors for N occurrences

#### Scenario: Requirement names differing only by case are not duplicates
- **WHEN** a main spec declares two `### Requirement:` headers whose names differ only in letter case (for example `Works` and `works`)
- **THEN** the validator SHALL NOT report a `spec-duplicate-requirement` error, because the 1.8 CLI's duplicate match is case-sensitive and reports the file `valid` on that account

#### Scenario: Duplicate detection does not apply to change delta specs
- **WHEN** a change delta spec repeats a `### Requirement:` name across its `## ADDED`/`## MODIFIED` sections (or within one section)
- **THEN** the built-in validator SHALL NOT emit a `spec-duplicate-requirement` error for the delta spec, because that main-spec rule is scoped to `openspec/specs/**`; the CLI's separate delta-consistency checks are not reimplemented in the fallback, keeping the fallback no more restrictive than the CLI on delta specs

#### Scenario: Title and clause severities
- **WHEN** a main spec has no `# Title` heading
- **THEN** that SHALL be a WARNING (`spec-title-required`)
- **WHEN** a scenario lacks a `WHEN` or `THEN` clause
- **THEN** that SHALL be an INFO (`spec-scenario-clauses`)

#### Scenario: RFC keyword severity follows the body
- **WHEN** a requirement body has prose but no whole-word `SHALL` or `MUST`
- **THEN** the issue SHALL be a WARNING (`spec-rfc-keywords`, or `spec-rfc-keyword-in-header` when the keyword sits only in the header), re-promoted under a strict run
- **WHEN** a requirement has no body prose at all
- **THEN** it SHALL remain an ERROR (`spec-rfc-keywords`)

#### Scenario: Fenced code is masked
- **WHEN** keyword or scenario presence is evaluated
- **THEN** fenced code blocks SHALL be masked, so a keyword appearing only in a fence does not satisfy the check

#### Scenario: Header-only keyword quick-fix
- **WHEN** the only keyword sits in the requirement header
- **THEN** a targeted diagnostic SHALL direct the author to move it onto a body line, with a quick-fix offered

#### Scenario: Duplicate requirement names
- **WHEN** a name repeats N times in one file
- **THEN** the later N-1 occurrences SHALL each be flagged, referencing the line of the first declaration and anchored on the occurrence's line
- **AND** this rule SHALL NOT apply to change delta specs, whose names legitimately recur across sections

### Requirement: Per-run strict validation

Strict validation SHALL be a per-invocation choice, mirroring `validate --strict`, with no persisted strict preference. A separate `OpenSpec.ValidateStrict` action SHALL select it, and the default Validate SHALL stay non-strict. A strict run SHALL be threaded as a per-invocation boolean, never stored on a shared service. A strict run SHALL never be silent.

#### Scenario: Strict is a per-run action, not a persistent setting
- **WHEN** the user wants strict validation
- **THEN** they SHALL invoke the `OpenSpec.ValidateStrict` action (toolbar dropdown, menu, or Find Action); there SHALL be no persistent "strict validation" setting anywhere

#### Scenario: Default Validate is non-strict
- **WHEN** the user invokes the default `OpenSpec.Validate`
- **THEN** the run SHALL use the CLI/plugin default verdict (warnings do not fail), with no `--strict` passed to the CLI

#### Scenario: Strict passes --strict to the CLI when present
- **WHEN** a strict run executes with the CLI available
- **THEN** the CLI invocation SHALL include `--strict`, the console SHALL echo `openspec validate --all --strict`, and the verdict SHALL come from each item's `valid` field

#### Scenario: CLI-absent strict flips the verdict on warnings
- **WHEN** a strict run executes with the CLI absent and the built-in fallback produces a result whose only issues are WARNINGs
- **THEN** the verdict SHALL flip to FAILED only if at least one WARNING is in the CLI-mirroring strict-warning set (a warning the real CLI also emits and fails on under `--strict`) — currently the missing-`SHALL`/`MUST` warning (`spec-rfc-keywords`/`spec-rfc-keyword-in-header`); a fallback whose WARNINGs are each either a plugin-invented lint the CLI never emits (`spec-title-required`, `change-artifact-missing`, `change-schema-incompatible`, `delta-removed-fields`) or a condition the CLI reports as an ERROR rather than a warning (`delta-spec-sections`) SHALL PASS, because the plugin MUST NOT be more restrictive than the CLI
- **AND** when a flip does occur, the warning issues SHALL remain labeled WARNING (not re-severity-ed), and the result SHALL disclose that strict counted the warnings as failures

#### Scenario: Missing-keyword warning flips a strict fallback verdict
- **WHEN** a strict run executes with the CLI absent and the built-in fallback's only issue on an otherwise-valid main spec is a body-carrying requirement lacking `SHALL`/`MUST` (a `spec-rfc-keywords` WARNING)
- **THEN** the default-mode verdict SHALL pass but the strict-mode verdict SHALL FAIL, because the missing-keyword warning is in the CLI-mirroring strict-warning set, matching the 1.8 CLI which reports that spec `valid` in default and `valid:false` under `--strict`

#### Scenario: CLI-absent strict does not flip on a plugin-invented lint warning
- **WHEN** a strict run executes with the CLI absent and the built-in fallback's only issue on an otherwise-valid item is a plugin-invented lint WARNING (e.g. `change-artifact-missing` on a change with no `tasks.md`, or `spec-title-required` on a title-less spec) that the real CLI reports strict-valid
- **THEN** the strict fallback verdict SHALL PASS for that item, matching the CLI's strict verdict

#### Scenario: Strict discloses itself in the summary
- **WHEN** a strict run completes
- **THEN** the summary notification title SHALL read `Validate (strict)`; a warnings-only failure SHALL append `— strict: warnings count as failures` after the `failed (…)` summary, preserving the `failed (` token

#### Scenario: One-time migration notice for a prior strict-on user
- **WHEN** the plugin upgrades and detects that the removed persistent `strictValidation` setting had been enabled
- **THEN** it SHALL show a single one-time notification directing the user to the per-run `Validate (Strict)` action, then never show it again; a user on the default (off) SHALL see no notice

#### Scenario: Built-in fallback under strict
- **WHEN** the CLI is absent and a strict run is requested
- **THEN** the fallback SHALL fail on any ERROR, and on a WARNING only when it is in the CLI-mirroring strict set: the missing-`SHALL`/`MUST` warnings (`spec-rfc-keywords`, `spec-rfc-keyword-in-header`)
- **AND** every other built-in non-config WARNING SHALL NOT fail the strict verdict, and config guidance SHALL stay non-failing

#### Scenario: Strict run output
- **WHEN** a strict run completes
- **THEN** the summary notification SHALL be titled `Validate (strict)`, a warnings-only failure SHALL append `— strict: warnings count as failures`, and the console SHALL echo the `--strict` command
- **AND** the literal `passed (` and `failed (` summary tokens SHALL be preserved

### Requirement: Change validation

The plugin SHALL validate each active change's `.openspec.yaml` for schema compatibility. A schema is recognized when it appears in the union of the built-in set and the CLI-runtime set from `SchemaService.listSchemas()`. Two plugin-invented lints SHALL always be non-failing WARNINGs: `change-artifact-missing` and `change-proposal-required`. Neither SHALL be escalated to ERROR, nor fail a strict run.

#### Scenario: Proposal required
- **WHEN** a change has no `proposal.md`
- **THEN** the validator SHALL report a non-failing WARNING with rule `change-proposal-required` — never an ERROR and never flipping a strict verdict, because the real CLI validates a proposal-less change that has a valid delta as valid (it resolves changes by directory existence, not by requiring `proposal.md`), so failing on it would make the plugin more restrictive than the CLI it wraps

#### Scenario: Required artifacts for version
- **WHEN** the declared version requires specific artifacts and a change is missing one
- **THEN** the validator SHALL report a WARNING with rule `change-artifact-missing` — always a non-failing WARNING, never escalated to ERROR by a persistent setting and never flipping a strict verdict, because the CLI never emits it

#### Scenario: Change schema incompatible with project version
- **WHEN** a change's `.openspec.yaml` declares a schema that is neither in the built-in fallback set nor in `SchemaService.listSchemas()`
- **THEN** the validator SHALL report a WARNING with rule `change-schema-incompatible`. The warning text SHALL list the known-set and indicate CLI status so the user understands why a custom-forked schema might not be visible.

#### Scenario: Custom-forked schema accepted for change when CLI lists it
- **WHEN** the user has forked a custom schema via `openspec schema fork` AND a change's `.openspec.yaml` declares that custom schema
- **THEN** the validator SHALL NOT report `change-schema-incompatible`

#### Scenario: Why the lints are non-failing
- **WHEN** the lints are considered
- **THEN** they SHALL stay non-failing, because the CLI never emits them: it does not check artifact-file presence, and it resolves changes by directory, not by a required `proposal.md` (upstream #1182)

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

The plugin SHALL validate delta spec files: section headings, removal metadata, scenario coverage and rename FROM/TO structure. It SHALL discover delta specs recursively under `specs/`, matching the CLI, and report a regular `spec.md` directly at the `specs/` root as a misplaced-delta ERROR. A change whose schema requires specs and has no delta SHALL get a no-deltas ERROR, suppressed for `skip_specs: true`. INFO issues SHALL never affect the verdict.

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

#### Scenario: Non-canonical header in a delta section gets an INFO hint
- **WHEN** an ADDED or MODIFIED section contains a level-3 header that is not a named `### Requirement:` header (e.g. `### Implementation notes`, or a nameless `### Requirement:`)
- **THEN** the validator SHALL report an INFO-severity issue with code `delta-skipped-header` anchored to that header's line, and the file's verdict SHALL be unaffected by it

#### Scenario: Renamed section missing FROM/TO
- **WHEN** a `## RENAMED Requirements` section has a `FROM:` line carrying the requirement header with no `TO:` line after it, or a `TO:` line with no `FROM:` before it, as OpenSpec's reader pairs them
- **THEN** the validator SHALL report an ERROR with code `delta-renamed-fields` and OpenSpec's message (`RENAMED FROM: "<name>" has no matching TO: line. Write each rename as a FROM: line followed immediately by its TO: line.`)

#### Scenario: An empty RENAMED section is not an error
- **WHEN** a `## RENAMED Requirements` section contains no `FROM:` or `TO:` line at all (it is empty, or holds only HTML-comment guidance such as a freshly scaffolded delta), and the built-in validator or the delta IDE inspection evaluates it
- **THEN** neither SHALL report `delta-renamed-fields` for that section, matching the OpenSpec client, which reports a change `valid:true` when its delta has an empty RENAMED section beside a parsed ADDED requirement — so the fallback is never more restrictive than the client

#### Scenario: Renamed section with valid FROM/TO
- **WHEN** a `## RENAMED Requirements` section contains one or more `FROM:`/`TO:` pairs carrying the requirement header (non-bullet form, or bulleted with any of `-`, `*`, `+`)
- **THEN** the validator SHALL NOT report `delta-renamed-fields` or `delta-spec-sections` for that file

#### Scenario: Star- or plus-bulleted RENAMED pair is accepted, matching CLI 1.13
- **WHEN** a `## RENAMED Requirements` section's only pair is written with `*` or `+` bullets (e.g. ``* FROM: `### Requirement: Old` `` followed by ``* TO: `### Requirement: New` ``), and the built-in validator or the delta IDE inspection evaluates it
- **THEN** neither SHALL report `delta-renamed-fields`, matching CLI 1.13.0+, which parses every CommonMark bullet marker and reports such a change `valid:true` — so the fallback is never more restrictive than the client

#### Scenario: Verdict parity with the 1.6 CLI
- **WHEN** the verdict-parity corpus (specs and delta files exercising the keyword, fence, scenario, and skipped-header rule classes) is validated by the plugin and by the captured real 1.6.0 CLI output
- **THEN** the plugin's per-case valid/invalid verdict SHALL match the CLI's `valid` flag for every case

#### Scenario: Delta spec at the specs root is a misplaced-delta error
- **WHEN** a change has a regular file named exactly `spec.md` directly at its `specs/` root (no capability directory), and the built-in validator is the verdict
- **THEN** the validator SHALL report an ERROR with code `delta-spec-misplaced`, matching the CLI which reports such a change `valid:false` because a root-level delta is dropped on apply/archive, and the file's verdict SHALL fail

#### Scenario: Delta nested under a capability path is discovered and validated
- **WHEN** a change's delta lives at `specs/<capability-path>/spec.md`, including a multi-segment path such as `specs/<area>/<capability>/spec.md`
- **THEN** the validator SHALL discover it as a delta and apply the delta structural rules to it (it SHALL NOT be reported as misplaced), matching the CLI which accepts a `spec.md` at any depth of at least one directory below `specs/`

#### Scenario: Change requiring specs with no deltas is an error
- **WHEN** a change whose schema requires specs has no parsed delta entries across its delta `spec.md` files (an empty or missing `specs/` directory, only non-`spec.md` files, or delta files whose sections hold no entries) and does not declare `skip_specs`
- **THEN** the validator SHALL report an ERROR with code `delta-none-found`, matching the CLI which reports such a change `valid:false`

#### Scenario: A skip_specs change with no deltas is not an error
- **WHEN** a change declares `skip_specs: true` in its `.openspec.yaml` and has no delta `spec.md`
- **THEN** the validator SHALL NOT report a `delta-none-found` error, matching the CLI which treats a `skip_specs` change with no deltas as valid

#### Scenario: A misplaced delta does not also trigger the no-deltas error
- **WHEN** a change's only delta is a misplaced `spec.md` at the `specs/` root
- **THEN** the validator SHALL report the `delta-spec-misplaced` ERROR only, and SHALL NOT additionally report `delta-none-found`, matching the CLI which reports the misplaced issue alone

#### Scenario: A delta file with sections but no entries is an error
- **WHEN** a delta `spec.md` has one or more delta sections but OpenSpec's reader parses no entry from any of them (for example a RENAMED section holding only bare `FROM: Old` / `TO: New` lines)
- **THEN** the validator SHALL report an ERROR with code `delta-no-entries` and OpenSpec's message (`Delta sections <sections> were found, but no requirement entries parsed. Ensure each section includes at least one "### Requirement:" block (REMOVED may use bullet list syntax).`), matching every supported CLI from 1.3.0

#### Scenario: Bare rename lines beside other entries are ignored, not errors
- **WHEN** a delta `spec.md` holds a parsed entry (such as an ADDED requirement) and a RENAMED section with only bare `FROM: Old` / `TO: New` lines
- **THEN** the validator and the delta IDE inspection SHALL report no error for the bare lines, matching every supported CLI, which ignores them and reports the change valid

#### Scenario: Entries recognized as the reader does
- **WHEN** delta entries are read
- **THEN** they SHALL be recognized through the shared merge parser: `### Requirement:` blocks, REMOVED bullets and `FROM:`/`TO:` lines carrying the header
- **AND** a bare `FROM: Old` line SHALL NOT be an entry

#### Scenario: Non-canonical level-3 headers
- **WHEN** an ADDED or MODIFIED section has a level-3 header the parser skips
- **THEN** an INFO issue anchored to its line SHALL hint that a nameless requirement header needs a name, or that other level-3 headers are ignored unless written as `### Requirement: <name>`

#### Scenario: Misplaced root spec file
- **WHEN** a `spec.md` sits directly at the `specs/` root
- **THEN** a misplaced-delta ERROR (`delta-spec-misplaced`) SHALL be raised, and it counts as a delta found, so a change whose only delta is misplaced yields that ERROR alone
- **AND** a `spec.md` at any depth of one directory or more SHALL be validated as a delta

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

The plugin SHALL distinguish between scaffolding placeholder content and real authored content as a hint only. It SHALL NOT change an artifact's status: the status is the one `openspec status` reports, where an artifact is done when its file exists.

#### Scenario: Placeholder detection
- **WHEN** an artifact file contains only template placeholders or boilerplate headings with no substantive content
- **THEN** the plugin SHALL keep the status `openspec status` reports (DONE) and mark the artifact as looking like an unfilled template, without disabling any action

### Requirement: Artifact file watching

The plugin SHALL auto-detect artifact file changes via VFS listeners and refresh pipeline status after clipboard or editor delivery.

#### Scenario: File save triggers refresh
- **WHEN** a user saves an artifact file after pasting AI-generated content
- **THEN** the plugin SHALL detect the VFS change and update the artifact's pipeline status

### Requirement: Validate from the Project View context menu

The plugin SHALL offer Validate in the Project View context menu, visible only under an `openspec/` directory. It SHALL validate the spec or active change that owns the selection, and the whole project otherwise. It SHALL reuse the shared pipeline scoped to that target, SHALL NOT fabricate a per-file verdict, and SHALL check visibility by path only, with no blocking I/O or CLI call.

#### Scenario: Menu item hidden outside openspec
- **WHEN** the user right-clicks a file that is not under an `openspec/` directory
- **THEN** the Validate OpenSpec menu item SHALL NOT be shown

#### Scenario: Validating a spec from the tree
- **WHEN** the user right-clicks a file under `openspec/specs/<capability>/` and invokes Validate
- **THEN** the plugin SHALL validate that spec (not the whole project) and report the result to the console

#### Scenario: Validating a change from the tree
- **WHEN** the user right-clicks a file under an active change's `openspec/changes/<name>/` and invokes Validate
- **THEN** the plugin SHALL validate that change and report the result

#### Scenario: Non-item selection falls back to whole-project
- **WHEN** the user invokes Validate on a selection under `openspec/changes/archive/`, at the `openspec/` root, or on `config.yaml`
- **THEN** the plugin SHALL validate the whole project rather than fabricating a single-item verdict for a non-validatable target

#### Scenario: Target mapping
- **WHEN** the selection is under `openspec/specs/<capability>/`
- **THEN** that spec SHALL be validated
- **WHEN** it is under `openspec/changes/<name>/` for an active change
- **THEN** that change SHALL be validated
- **WHEN** it is under `openspec/changes/archive/`, at the `openspec/` root, or on a file such as `config.yaml`
- **THEN** the whole project SHALL be validated

### Requirement: Validation result presentation

The plugin SHALL present validation results in the OpenSpec console as a structured report grouped by file. Files with an ERROR SHALL sort before warning-only files, then info-only files, and issues within a group SHALL sort by line. Each reported location SHALL be a link that opens the file at that line. The presentation SHALL NOT add a per-file verdict or any new severity, and the balloon SHALL remain a summary that does not list issues.

#### Scenario: Header states verdict, target, and counts
- **WHEN** a validation run completes for a target (whole project, a change, or a spec)
- **THEN** the console SHALL show a verdict line naming the target (e.g. `Validation FAILED — Change <name>` or `Validation PASSED — <target>`) followed by a count line summarizing the number of errors, warnings, and infos

#### Scenario: Issues grouped by file, errors-first
- **WHEN** a failing result contains issues across multiple files
- **THEN** the console SHALL present each file as a group header with its issues beneath, ordering files that contain an ERROR before warning-only files and info-only files, and ordering issues within a file by line ascending

#### Scenario: Resolvable location is a clickable hyperlink
- **WHEN** an issue carries a file path that resolves to a file on disk and a 1-based line
- **THEN** the console SHALL render that issue's location as a hyperlink that, when clicked, opens the file with the caret at the reported line (mapping the 1-based line to the editor's 0-based position)

#### Scenario: Unresolvable location degrades to plain text
- **WHEN** an issue carries a path that does not resolve to a file on disk (e.g. a CLI-reported `type/id` pseudo-path) or has no usable line
- **THEN** the console SHALL render that issue as plain, still-severity-colored text with no hyperlink, rather than a dead link or an error

#### Scenario: Severity determines color
- **WHEN** issues of differing severity are rendered
- **THEN** ERROR, WARNING, and INFO SHALL each render in a distinct, theme-driven console content type so severity is distinguishable, and no new severity beyond ERROR/WARNING/INFO SHALL be introduced

#### Scenario: Clean pass shows a concise confirmation
- **WHEN** a validation run passes with zero issues
- **THEN** the console SHALL show a concise pass confirmation naming the target and a "no issues" line, rather than an empty or ambiguous block

#### Scenario: Passing-with-warnings still lists the warnings
- **WHEN** a run passes (no errors) but reports one or more warnings or infos
- **THEN** the console SHALL show the pass verdict and count line and still list the warning/info issues grouped by file

#### Scenario: Presentation invents no per-file verdict
- **WHEN** results are grouped under file headers
- **THEN** the file headers SHALL serve only as grouping keys and SHALL NOT display a per-file valid/invalid badge, because the model carries a single result-level verdict and no per-file verdict

#### Scenario: Themed severity rendering
- **WHEN** issues are rendered
- **THEN** each severity SHALL use a distinct, theme-driven console content type, and rendering SHALL run on the UI thread

#### Scenario: Single shared path
- **WHEN** any caller shows validation results (the toolbar Validate, the Project View Validate or a future caller)
- **THEN** it SHALL use the same result-rendering path

### Requirement: Item-less CLI error response is a failed run

When a `validate --json` run returns no `items` array but reports error-severity entries in a top-level `status` array, the plugin SHALL treat the run as failed and fall back to the built-in verdict, never a pass with zero items. A response with an `items` array SHALL keep taking its verdict from each item's `valid` field.

#### Scenario: invalid_item error response does not report PASS
- **WHEN** the CLI's `validate --json` output is the captured 1.13.2 `invalid_item` error response (no `items`, a `status` entry with `"severity":"error"`)
- **THEN** the plugin SHALL NOT report the run as passed, and SHALL use the built-in validator's result as the verdict

#### Scenario: A normal items response is unaffected
- **WHEN** the CLI's `validate --json` output carries an `items` array
- **THEN** the plugin SHALL derive pass/fail from each item's `valid` field exactly as before, regardless of any top-level `status` entries

### Requirement: Unreadable CLI validate output is a failed run

When `validate --json` output cannot be read as a verdict, the plugin SHALL treat the CLI run as failed and use the built-in validator's result as the verdict, as it does when the CLI cannot run. Unreadable output includes malformed or truncated JSON, and a JSON object with neither an `items` array nor an error `status` entry. Such output SHALL NOT be reported as a pass with zero issues.

#### Scenario: Truncated JSON does not pass
- **WHEN** the CLI's `validate --json` stdout begins with `{` but is not complete JSON
- **THEN** the plugin SHALL NOT report the run as passed, and SHALL use the built-in validator's result as the verdict

#### Scenario: A JSON object without a verdict does not pass
- **WHEN** the CLI's `validate --json` stdout is a JSON object with no `items` array and no error `status` entry
- **THEN** the plugin SHALL use the built-in validator's result as the verdict

#### Scenario: Captured no-root response falls back
- **WHEN** the CLI's `validate --all --json` output is the captured 1.14.3 `no_openspec_root` response
- **THEN** the plugin SHALL use the built-in validator's result as the verdict

#### Scenario: A real report with failing items is unaffected
- **WHEN** the CLI exits 1 with an `items` array whose failing items carry ERROR issues
- **THEN** the plugin SHALL take the verdict and issues from the items exactly as before

### Requirement: Configuration error response is a failed run

When the CLI is available and `validate --json` answers with a configuration error (CLI 1.16+: an item-less response whose status code is `unparseable_config` or `unreadable_config`), the plugin SHALL report the run as failed, with one ERROR at `config.yaml` whose text is the CLI's `message` and `fix`. It SHALL NOT merge in the built-in result. Other item-less error responses SHALL keep falling back.

#### Scenario: Unparseable config fails Validate with the CLI's fix
- **WHEN** the CLI's `validate --all --json` output is the captured 1.16.0 `unparseable_config` response
- **THEN** the plugin SHALL report the whole-project run as failed with one ERROR at `config.yaml` whose text contains the captured `message` and `fix`, and SHALL NOT use the built-in validator's verdict

#### Scenario: Unreadable config fails Validate with the CLI's fix
- **WHEN** the CLI's `validate --all --json` output is the captured 1.16.0 `unreadable_config` response
- **THEN** the plugin SHALL report the run as failed with one ERROR at `config.yaml` whose text contains the captured `message` and `fix`

#### Scenario: A single-item or strict run fails the same way
- **WHEN** a single change or spec is validated, or the run is `--strict`, and the CLI answers with a configuration error response (1.16 checks `config.yaml` before every target and in both modes)
- **THEN** the plugin SHALL report that run as failed with the same single ERROR, not with the built-in verdict for the item, and SHALL merge no built-in spec, change or config issues, because the CLI validated nothing else

#### Scenario: Other error responses still fall back
- **WHEN** the CLI answers with any other item-less error response, such as the captured `invalid_item` or `no_openspec_root` response
- **THEN** the plugin SHALL use the built-in validator's result as the verdict, as before
