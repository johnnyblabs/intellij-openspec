## ADDED Requirements

### Requirement: The built-in validator follows a CLI release

The built-in validator SHALL apply each rule a CLI release introduced only when it follows that release or a later one. It SHALL follow the detected CLI's version, the plugin's target release when no CLI is detected, and no release-gated rule when the detected version can't be read. Its verdicts SHALL match each captured release's on the release-parity corpus, except where it is knowingly laxer.

#### Scenario: A detected CLI decides the rules
- **WHEN** CLI 1.15.0 is detected and its validate run fails, so the built-in validator answers
- **THEN** the validator SHALL apply 1.15.0's rules, without the 1.16 step check or the 2.1.1 growth check

#### Scenario: No CLI follows the target release
- **WHEN** no CLI is detected
- **THEN** the validator SHALL follow the plugin's target release

#### Scenario: An unreadable version applies no gated rule
- **WHEN** a CLI is detected but its version can't be read as a release number
- **THEN** the validator SHALL apply none of the release-gated rules

#### Scenario: Verdicts match each captured release
- **WHEN** the built-in validator, following a captured release, validates the release-parity corpus in default and in strict mode
- **THEN** its verdict for every item SHALL equal that release's `valid` flag, except the documented items where the release fails a warning the plugin leaves to the CLI, on which the plugin SHALL pass

### Requirement: Long requirements are flagged from CLI 1.14.1

From release 1.14.1, a main-spec requirement or an ADDED delta requirement whose requirement text is over 500 characters SHALL be a WARNING with code `spec-requirement-too-long`, which fails a strict run. The text SHALL be measured as the CLI measures it: body lines up to the first heading, without blank, fenced or metadata-only lines, trimmed and joined by newlines.

#### Scenario: A long main-spec requirement
- **WHEN** a main-spec requirement's text is 501 characters and the release followed is 1.14.1 or later
- **THEN** the validator SHALL report a `spec-requirement-too-long` WARNING, the default verdict SHALL pass, and a strict run SHALL fail

#### Scenario: Exactly at the limit
- **WHEN** the text is exactly 500 characters
- **THEN** the validator SHALL NOT report it

#### Scenario: A long ADDED requirement
- **WHEN** an ADDED delta requirement's text is over 500 characters
- **THEN** the validator SHALL report a `spec-requirement-too-long` WARNING for that delta

#### Scenario: An older release
- **WHEN** the release followed is below 1.14.1
- **THEN** the validator SHALL NOT report the too-long warning

### Requirement: Scenarios need a trigger and an outcome from CLI 1.16

From release 1.16.0, a `Scenario:`-labeled level-4 block (label in any case) with a body but no trigger (WHEN or GIVEN) or no outcome (THEN) SHALL be a WARNING `spec-scenario-steps`, in main specs and ADDED and MODIFIED deltas. A keyword SHALL count when bolded in any case or as a bare uppercase word on a body line. The header and fenced lines SHALL NOT count; `AND` adds nothing by itself.

#### Scenario: Missing trigger in a main spec
- **WHEN** a main-spec scenario has `- **THEN** y` but no WHEN or GIVEN step, and the release followed is 1.16.0 or later
- **THEN** the validator SHALL report a `spec-scenario-steps` WARNING, the default verdict SHALL pass, and a strict run SHALL fail

#### Scenario: Missing outcome in an ADDED delta
- **WHEN** an ADDED delta requirement's scenario has `- **GIVEN** x` but no THEN step
- **THEN** the validator SHALL report a `spec-scenario-steps` WARNING for that delta

#### Scenario: How a step is written
- **WHEN** a scenario's lines read `- **Given:** x`, `- __when__ y`, `- THEN z`, `- **AND** WHEN a` or `<!-- THEN -->`
- **THEN** each SHALL supply its keyword, while a lowercase bare `when`, an `AND` line with no keyword, a keyword in the scenario header and a keyword inside a fenced block SHALL NOT

#### Scenario: A scenario header with no body
- **WHEN** a scenario header has no body under it
- **THEN** the step check SHALL NOT report it, because that header already fails on its own

#### Scenario: Removed and renamed requirements
- **WHEN** a delta has REMOVED or RENAMED requirements
- **THEN** the step check SHALL NOT apply to them

### Requirement: A MODIFIED requirement that grows too long is flagged from CLI 2.1.1

From release 2.1.1, a MODIFIED requirement whose text is over 500 characters and longer than the main spec's current text SHALL be a WARNING `delta-modified-too-long`, failing a strict run. The current text SHALL be that of the main-spec requirement of that name, or of the one it was renamed from, following rename chains. With no such requirement, or no main spec, there SHALL be no check.

#### Scenario: A short requirement grows past the limit
- **WHEN** a MODIFIED delta replaces a short main-spec requirement with text over 500 characters, and the release followed is 2.1.1
- **THEN** the validator SHALL report a `delta-modified-too-long` WARNING, the default verdict SHALL pass, and a strict run SHALL fail

#### Scenario: An older release
- **WHEN** the same delta is validated following 2.1.0 or earlier
- **THEN** the validator SHALL NOT report the growth warning

#### Scenario: Already long in the main spec
- **WHEN** the main spec's requirement text is already at least as long as the MODIFIED text
- **THEN** the validator SHALL NOT report the growth warning

#### Scenario: A renamed requirement
- **WHEN** a delta renames a short main-spec requirement and MODIFIES it under the new name with text over 500 characters
- **THEN** the validator SHALL compare against the renamed-from requirement and report the growth warning

### Requirement: Delta front matter is left with the change from CLI 2.1.1

From release 2.1.1, a front-matter block at the start of a delta spec SHALL be ignored by the built-in validator. It is front matter only when line 1 is exactly `---`, its first non-comment line looks like a key, and it closes with `---` before any `##` heading or requirement header. The plugin's own merge SHALL never carry front matter into a main spec, from a delta or the main spec.

#### Scenario: A delta that starts with front matter
- **WHEN** a delta spec starts with `---`, `owner: team-a`, `---`, followed by valid delta sections, and the release followed is 2.1.1
- **THEN** the built-in validator SHALL report no issue for the front matter, and the change SHALL validate as it would without it

#### Scenario: Not front matter
- **WHEN** a delta spec starts with ` ---` (indented), or with `---` followed by prose with no key, by a `##` heading before the next `---`, or by no closing `---`
- **THEN** the block SHALL NOT be treated as front matter

#### Scenario: A byte-order mark or CRLF line endings
- **WHEN** the delta starts with a byte-order mark or uses CRLF line endings
- **THEN** the block SHALL be recognized as it would be without them

#### Scenario: Merging a delta with front matter
- **WHEN** the plugin's own merge applies a delta that starts with front matter, or writes to a main spec that starts with it
- **THEN** the merged main spec SHALL NOT receive the delta's front-matter lines, and the main spec's own Purpose SHALL be read past its front matter

## MODIFIED Requirements

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

#### Scenario: Release-gated rules sit outside the anchor guard
- **WHEN** the shared verdict-parity corpus is compared with the laxest anchor generation
- **THEN** the built-in validator SHALL follow the anchor release for that comparison, so rules a later release introduced do not apply there, and those rules SHALL instead be held to each release's own captured verdicts (see "The built-in validator follows a CLI release")

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
- **WHEN** the built-in validator follows a release below 1.16.0 and a `#### Scenario:` block is missing a `WHEN` clause, a `THEN` clause, or both
- **THEN** the validator SHALL report an INFO with code `spec-scenario-clauses` and the file's verdict SHALL be unaffected, because those releases perform no clause-structure validation

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
- **WHEN** a scenario lacks a `WHEN` or `THEN` clause and the release followed is below 1.16.0
- **THEN** that SHALL be an INFO (`spec-scenario-clauses`); from 1.16.0 the step check applies instead

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
- **THEN** the verdict SHALL flip to FAILED only if at least one WARNING is in the CLI-mirroring strict-warning set for the release followed (a warning that release also emits and fails on under `--strict`) — the missing-`SHALL`/`MUST` warning (`spec-rfc-keywords`/`spec-rfc-keyword-in-header`) on every release, the too-long warning (`spec-requirement-too-long`) from 1.14.1, the scenario step warning (`spec-scenario-steps`) from 1.16.0, and the MODIFIED growth warning (`delta-modified-too-long`) from 2.1.1; a fallback whose WARNINGs are each either a plugin-invented lint the CLI never emits (`spec-title-required`, `change-artifact-missing`, `change-schema-incompatible`, `delta-removed-fields`) or a condition the CLI reports as an ERROR rather than a warning (`delta-spec-sections`) SHALL PASS, because the plugin MUST NOT be more restrictive than the CLI
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
- **THEN** the fallback SHALL fail on any ERROR, and on a WARNING only when it is in the CLI-mirroring strict set for the release followed: the missing-`SHALL`/`MUST` warnings (`spec-rfc-keywords`, `spec-rfc-keyword-in-header`), `spec-requirement-too-long` from 1.14.1, `spec-scenario-steps` from 1.16.0, and `delta-modified-too-long` from 2.1.1
- **AND** every other built-in non-config WARNING SHALL NOT fail the strict verdict, and config guidance SHALL stay non-failing

#### Scenario: Strict run output
- **WHEN** a strict run completes
- **THEN** the summary notification SHALL be titled `Validate (strict)`, a warnings-only failure SHALL append `— strict: warnings count as failures`, and the console SHALL echo the `--strict` command
- **AND** the literal `passed (` and `failed (` summary tokens SHALL be preserved
