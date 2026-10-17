## MODIFIED Requirements

### Requirement: CLI verdict is authoritative when the CLI is available

When the OpenSpec CLI is available and a `validate` run completes successfully with parseable output, the plugin SHALL treat the CLI's result as authoritative for the artifacts the CLI validates — specs and change deltas — rather than overriding it with the built-in validator's own opinion. The plugin SHALL derive each CLI-validated item's pass/fail from that item's `valid` field in the CLI output (mirroring the client's own rule `valid = strictMode ? errors===0 && warnings===0 : errors===0`), not by re-deriving pass/fail from the severities of the issues the plugin extracted. The built-in validator SHALL NOT contribute spec or change issues that fail the merged verdict when the CLI has produced a verdict for those artifacts.

Because the CLI's `validate` never reads `openspec/config.yaml`, the built-in validator SHALL remain the sole owner of `config.yaml` validation in all paths. For a whole-project validation with the CLI available, the merged verdict SHALL be `cli.passed() && builtInConfig.passed()`, where `builtInConfig` is the built-in validator scoped to `config.yaml`, and the displayed issues SHALL be the union of the CLI's spec/change issues and the built-in's config issues. For a single-spec or single-change target with the CLI available, the verdict SHALL be the CLI's alone (no config component). When the CLI is unavailable, or its run fails or emits unparseable output, the plugin SHALL fall back to the full built-in validator as the verdict — never to a blind pass.

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
