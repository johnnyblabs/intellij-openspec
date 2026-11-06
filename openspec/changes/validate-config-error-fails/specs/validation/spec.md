## MODIFIED Requirements

### Requirement: CLI verdict is authoritative when the CLI is available

When the OpenSpec CLI is available and a `validate` run completes successfully with parseable output, the plugin SHALL treat the CLI's result as authoritative for the artifacts the CLI validates — specs and change deltas — rather than overriding it with the built-in validator's own opinion. The plugin SHALL derive each CLI-validated item's pass/fail from that item's `valid` field in the CLI output (mirroring the client's own rule `valid = strictMode ? errors===0 && warnings===0 : errors===0`), not by re-deriving pass/fail from the severities of the issues the plugin extracted. The built-in validator SHALL NOT contribute spec or change issues that fail the merged verdict when the CLI has produced a verdict for those artifacts.

The CLI's `validate` reports no `config.yaml` content issues (from 1.16 it only checks that the file can be read and parsed; see `Configuration error response is a failed run`), so the built-in validator SHALL remain the sole owner of `config.yaml` content validation in all paths. For a whole-project validation with the CLI available, the merged verdict SHALL be `cli.passed() && builtInConfig.passed()`, where `builtInConfig` is the built-in validator scoped to `config.yaml`, and the displayed issues SHALL be the union of the CLI's spec/change issues and the built-in's config issues. For a single-spec or single-change target with the CLI available, the verdict SHALL be the CLI's alone (no config component). When the CLI is unavailable, or its run fails or emits unparseable output, the plugin SHALL fall back to the full built-in validator as the verdict — never to a blind pass. The one exception is a configuration error response, which is the CLI's verdict on the run and SHALL be reported as a failure rather than replaced by the built-in verdict.

The never-more-restrictive-than-the-CLI guarantee SHALL be evidenced against captured real CLI output, and SHALL be durable across CLI generations. Because CLI generations may legitimately **relax** a default verdict (1.8.0 demoted the missing-`SHALL`/`MUST` rule to a non-failing warning, so more corpus items validate in default than under 1.6.0/1.7.0), the default-mode invariant SHALL be a **subset-of-the-laxest-anchor** relation rather than map-equality: the plugin's default verdict is proven to match the **laxest captured generation** (the default anchor — currently `1.8.0`), and every captured generation's default valid-set SHALL be a subset of that anchor's valid-set, so the plugin is by transitivity never more restrictive than any captured generation, and any future generation that relaxes a default verdict further than the anchor SHALL surface as a parity failure prompting the anchor to be advanced. Under `--strict` the invariant SHALL likewise be a **subset-of-the-strict-anchor** relation: every captured generation's `--strict` valid-set SHALL be a subset of the strict anchor's valid-set (the `1.6.0` default map, which `--strict` reproduced exactly on every generation through 1.13), so the plugin — proven to match the anchor — is never more restrictive than any captured generation under `--strict` either, including a generation whose new warnings reject more items (1.14.2 rejects exactly the corpus item whose pre-requirement text archiving would drop). This invariant is defined only over generations captured against the current corpus dialect; a generation whose own validation rules predate that dialect legitimately verdicts the corpus differently and is not part of the set.

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

### Requirement: Config validation

The plugin SHALL validate `openspec/config.yaml` for structural correctness when the file exists, applying only the rules that mirror upstream OpenSpec's own contract. The built-in validator SHALL own `config.yaml` content validation in all paths — including when the CLI is present, since the CLI's `validate` reports no `config.yaml` content issues. That ownership is **display-only and non-failing**: config validation SHALL NOT emit any ERROR-severity issue and SHALL NEVER fail a validation verdict, because `openspec validate` never fails on any `config.yaml` that it can read and parse (verified against the real CLI — a missing, empty, or unrecognized `schema` and any `version` value all validate clean, since upstream tolerates a missing schema and defaults to `spec-driven`). A file that cannot be read, or is not valid YAML, validates clean on CLI 1.3–1.15 and fails on CLI 1.16+; the plugin mirrors the 1.16 failure only through the CLI's own configuration error response (see `Configuration error response is a failed run`). Without that response the built-in check SHALL report such a file as a single non-failing **WARNING** — rule `config-unreadable` when the file cannot be read, rule `config-unparseable` when it is not valid YAML, each carrying the reason — that SHALL NOT fail the verdict in default or `--strict` mode, so the fallback is never more restrictive than an older supported client. YAML that parses to something other than a mapping (a scalar or a list) SHALL NOT be reported as a parse failure, because upstream parses it without error. The file itself SHALL be optional — matching upstream's `readProjectConfig() → null // No config is OK` semantics — so a project initialized with `openspec init` and never customized SHALL produce zero config-related validation issues. When the file does exist, validation SHALL enforce only the fields upstream defines in its Zod `ProjectConfigSchema` (`schema`, `context`, `rules`); fields the plugin reads as internal-only extensions (`version`, `profile`) SHALL NOT fire issues. A missing or empty `schema` field SHALL raise a single **INFO advisory** (rule `config-schema-required`) — a hygiene nudge that OpenSpec defaults to `spec-driven` — never a WARNING or ERROR, because a warning on a file the CLI validates clean would be stricter than the client the plugin wraps. Schema-name recognition SHALL be CLI-runtime-driven AND gated on an **authoritative** known-set: a `schema` value SHALL be checked for recognition only when the CLI is available and supports schema management (so the known-set reflects real project-local forks via `openspec schemas --json`); when the known-set is non-authoritative (CLI absent or below floor), the check SHALL be skipped rather than warn on a fork the plugin cannot see. A plugin-internal `version` value SHALL NOT raise any issue — it is stripped by upstream's Zod and never read by the CLI. Config parsing SHALL be lenient — unrecognized fields and type mismatches SHALL be silently ignored rather than raising parse errors. The config validation inspection SHALL guard against zero-length PSI elements before creating problem descriptors.

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

### Requirement: Item-less CLI error response is a failed run

When a CLI `validate --json` run returns a JSON object that carries **no `items` array** but reports one or more error-severity entries in a top-level `status` array (as CLI 1.13.1 does for a rejected item id, `{"status":[{"severity":"error","code":"invalid_item",...}]}`), the plugin SHALL treat the run as **failed** — not as a pass with zero items — and SHALL fall back to the built-in validator as the verdict, consistent with the rule that a failed or unparseable CLI run never yields a blind pass. A configuration error response (`unparseable_config` or `unreadable_config`) is not a fallback case: it SHALL be reported as a failure (see `Configuration error response is a failed run`). A response that carries an `items` array SHALL continue to take its verdict from each item's `valid` field, unchanged.

#### Scenario: invalid_item error response does not report PASS
- **WHEN** the CLI's `validate --json` output is the captured 1.13.2 `invalid_item` error response (no `items`, a `status` entry with `"severity":"error"`)
- **THEN** the plugin SHALL NOT report the run as passed, and SHALL use the built-in validator's result as the verdict

#### Scenario: A normal items response is unaffected
- **WHEN** the CLI's `validate --json` output carries an `items` array
- **THEN** the plugin SHALL derive pass/fail from each item's `valid` field exactly as before, regardless of any top-level `status` entries

## ADDED Requirements

### Requirement: Configuration error response is a failed run

When the CLI is available and a `validate --json` run answers with a configuration error response (CLI 1.16+: an item-less response whose error `status` code is `unparseable_config` or `unreadable_config`) the plugin SHALL report the run as **failed** with exactly one ERROR at the project's `config.yaml`, whose text is the CLI's `message` followed by its `fix`. It SHALL NOT fall back to, or merge in, the built-in validator's result. Every other item-less error response SHALL keep falling back.

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
