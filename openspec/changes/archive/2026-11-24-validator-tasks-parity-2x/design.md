# Design

## Context

The real CLIs 1.16.1, 1.17.0, 2.0.0, 2.1.0 and 2.1.1 were run on 2026-11-24 over one small project, in default and `--strict` modes:

| Item | 1.16.1 – 2.1.0 | 2.1.1 |
|---|---|---|
| Main-spec scenario with THEN but no WHEN/GIVEN | WARNING "is missing a WHEN/GIVEN step", strict fails | same |
| ADDED delta scenario with GIVEN but no THEN | WARNING "is missing a THEN step", strict fails | same |
| Delta starting with `---` / `owner: team-a` / `---` | WARNING "Text above the first "## " section is not carried into the main spec by archive", strict fails | no issue; archive warns "frontmatter (line 1) is not carried into the main spec. It stays with the change." |
| MODIFIED requirement grown from ~20 to ~560 characters | no issue | WARNING `MODIFIED "<name>": Requirement text is very long (>500 characters)…`, strict fails |
| Requirement with a single-line ```` ```inline``` ```` span | valid | valid |
| Unspaced `###Requirement:` in a main spec | invalid (ERROR "must have at least one scenario") | same |

What each release's source says:

- **The step check** (`findScenarioStepIssues` in `core/validation/validator.js`, with `scenarioStepCoverage` in `core/parsers/requirement-text.js`) is byte-identical from 1.16.1 to 2.1.1, and the v1.16.0 release note introduces it. Its rules, as the source and the runs show:
  - **Which blocks:** a level-4 block whose header is labeled `Scenario:` in any case (`#### scenario:` counts), and that has a body. It runs to the next `####`, so `#####` sub-headings and nested lists are part of it.
  - **Where:** main specs, and ADDED and MODIFIED deltas; never REMOVED or RENAMED.
  - **A trigger** is `**WHEN**`/`**GIVEN**` bolded in any case (with `**` or `__`, colon optional inside the bold), or a bare uppercase `WHEN`/`GIVEN` anywhere on a body line, prose included. **An outcome** is the same for `THEN`.
  - **What counts as a keyword:**
    - `AND` adds nothing by itself, but an `AND` line carrying a bare `WHEN` counts.
    - The header line and fenced lines are ignored.
    - HTML comments are **not** masked, so `<!-- THEN -->` counts.
  - **Severity:** a WARNING, which `--strict` turns into a failure.
- **The too-long check** (`validator.js`: ADDED blocks, and main-spec requirements) has been a WARNING since 1.14.1, per its release note "Strict requirement length", and is still an INFO in 1.6.0. It warns when the requirement text is over 500 characters: 500 passes, 501 warns.
- **Requirement text** (`extractRequirementText`, wrapping `extractRequirementBody`), the measure every length rule uses:
  - the lines after the header up to the first `#`–`######` heading, not only the first scenario;
  - blank and fenced lines dropped, each line trimmed, joined with `\n`;
  - `**Key**:` metadata lines dropped unless they are the whole body (a body that is only a metadata line is measured, and warns: `len-metadata-only`);
  - HTML comments counted.

  Two 250-character lines measure 501 and warn. A 600-character body made of one metadata line doesn't warn.
- **The growth check** (`findMainSpecModifiedIssues`, 2.1.1 only) compares a MODIFIED block's text with the main spec's current text, which is 0 when the main block has no body. It warns when the new text is over 500 and longer than the current text.
  - A MODIFIED under a new name is compared with the requirement it was renamed from, following rename chains.
  - A MODIFIED naming a renamed-away old name is skipped, as is one with no main spec or no such requirement.
- **Front matter** (`findFrontmatterEnd` in `core/parsers/requirement-blocks.js`, 2.1.1 only):
  - The lines are blanked rather than removed, so line numbers hold. A BOM and CRLF are normalized first.
  - Line 1 must be exactly `---`: a leading space disqualifies it.
  - Its first non-blank line that isn't a `#` comment must look like a key (`owner: a`, `"owner": a`, `owner:`). `# Title` followed by a key is therefore front matter. After the first key, every line is skipped until the closing `---`.
  - A `##`–`######` heading or a requirement header before the close means it is not front matter, and so does a block that never closes.
  - A block with comments but no key is not front matter. An empty block is.
  - 2.1.1's `specs-apply.js` also blanks front matter at the top of the *target main spec* before reading its Purpose. Main-spec validation is unchanged.
- **Wrapped task lines** (`parseLocatedTaskLines` in `utils/task-progress.js`, 2.0.0): a following line that doesn't end the paragraph is joined onto the task's description. The task count is unchanged.

The plugin today:

- **`BuiltInValidator`** reports a missing WHEN or THEN as INFO `spec-scenario-clauses`, on main specs only, and doesn't count GIVEN. Its `SCENARIO_PATTERN` is case-sensitive. It has no length or front-matter rule, and nothing in it depends on a CLI release. Its parity test holds it to the 1.8 captures, the laxest generation. `BuiltInValidatorRulesTest` and `BuiltInValidatorTest` assert the INFO hint with no CLI.
- **`SpecMerge`** is reached with a CLI present too: **Sync Specs** (`OpenSpecSyncAction`) calls `computeSync`/`applySync`. The plugin's own archive merge runs only without a CLI or below 1.5.
- **`OpenSpecValidateAction.CLI_MIRRORING_STRICT_WARNINGS`** is a fixed set: `spec-rfc-keywords` and `spec-rfc-keyword-in-header`.
- **`SpecFormatInspection` and `DeltaSpecInspection`** use only the validator's fence masking, not its rules, so the new warnings appear in Validate's results, not as editor highlights.
- **`TaskLines`** counts tasks, and nothing in the plugin reads a task's text: `ApplyPromptBuilder` inserts `tasks.md` whole.

## Goals / Non-Goals

**Goals:**
- With no CLI, or when its run fails, the plugin's verdict is the release's verdict, in default and in strict mode.
- Each rule appears only on the releases that have it.

**Non-Goals:**
- Reproducing the CLI's message text. Parity is about the verdict.
- The pre-2.1.1 "text above the first `##` section" warning, and the scenario-loss ERROR for MODIFIED. The plugin leaves both to the CLI and is laxer there, which the never-stricter rule allows.
- 2.1.1's other merge changes: a new spec's `## Purpose` skeleton and the retirement audit. These are merge, not validation, and get their own tracker entry.
- Reading task text. No surface needs it, and the count contract below guards the rule.

## Decisions

### 1. One "release followed", decided once per run

A small helper, `ValidationRelease.followed(project)`, returns the detected CLI's version when a CLI is detected. Otherwise it returns `PluginVersions.targetCliVersion()`. `BuiltInValidator` receives that version; its existing entry points keep their signatures and look the version up. Rules ask `CliVersion.atLeast(followed, "1.16.0")`.

A detected version that can't be parsed, or is below the plugin's floor, answers false to every `atLeast`, so no release-gated rule applies: the laxest outcome. Every test of a gated rule names the release it follows, because the target moves to 2.1.1 at the CLI bump. Existing tests that assert the INFO hint are pinned to 1.15.0, and the 1.8-anchor parity test follows 1.8.0.

- **Why the detected version:** the fallback also runs when a detected CLI's run fails. Then that CLI's own rules are the verdict the user would get from it.
- **Why the target with no CLI:** it's the release the plugin is built and tested against, and what a fresh install of the CLI would be. Today that's 1.17.0; after the CLI bump, 2.1.1.
- **Laxest-generation rule:** it remains the bound for rules that predate versioning. The 1.8 parity test stays as it is.

### 2. The step check

A port of `scenarioStepCoverage` and the labeled-header (case-insensitive) and has-body tests, with the scenario running to the next `####`. It applies to main specs and to ADDED and MODIFIED delta requirements, never REMOVED or RENAMED. Only fences are masked:
- **From 1.16.0:** a scenario missing a step is a WARNING `spec-scenario-steps`, naming which step is missing, and no INFO is emitted.
- **Below 1.16.0:** today's INFO `spec-scenario-clauses` stays, on main specs only.

Delta scenarios are new ground for the plugin. Below 1.16.0 they get nothing, as today.

### 3. A strict-failing set per release

`OpenSpecValidateAction.CLI_MIRRORING_STRICT_WARNINGS` becomes `strictWarnings(String release)`:
- always `spec-rfc-keywords` and `spec-rfc-keyword-in-header`;
- `spec-requirement-too-long` from 1.14.1;
- `spec-scenario-steps` from 1.16.0;
- `delta-modified-too-long` from 2.1.1.

`applyStrictFallbackVerdict` takes the release. The allow-list principle stays: a rule fails strict only where that release's CLI does.

### 4. Length rules: one measure, three checks

`RequirementText.of(block)` ports `extractRequirementText` exactly as the Context describes. The corpus pins it with 500- and 501-character bodies, a two-line body, an HTML comment and a metadata-only body, so a port that measures differently fails the real CLI's verdicts.

- **Too long**, from 1.14.1: a main-spec requirement or an ADDED delta requirement over 500 is `spec-requirement-too-long`.
- **Growth**, from 2.1.1: a MODIFIED requirement over 500 and longer than the current text is `delta-modified-too-long`.
  - The current text comes from the main spec at `openspec/specs/<capability-path>/spec.md`. It is the requirement of that name, or the one the delta's RENAMED section renamed into it, following chains.
  - A MODIFIED naming a renamed-away name, or one with no main spec or no such requirement, gets no check.

### 5. Front matter

A port of `findFrontmatterEnd`, with its BOM, CRLF, exact-first-line, comment and unclosed-block rules. From 2.1.1, the delta's leading front-matter lines are blanked before the delta is parsed, so line numbers hold.

`SpecMerge` blanks them whatever the release, because carrying front matter into a main spec is never right. That covers Sync Specs too. It also reads a target main spec's Purpose past that spec's own front matter, as 2.1.1 does. Below 2.1.1 the validator reads the delta as today. By the code, it reports nothing for the block (`SpecMerge.summarize` and `orphans` never read the preamble). The corpus run confirms that.

### 6. Parity corpora per release

The existing 1.6.0 corpus and the 1.8 oracle stay, with that test following 1.8.0. A second corpus, `fixtures/cli/2.1.1/parity-corpus-2x/`, holds:
- `step-missing-when` and `step-forms` as main specs, one scenario per form so each verdict is attributable:
  - bold in any case, `__`, colon inside the bold;
  - bare uppercase, lowercase prose;
  - AND-only, AND with a bare WHEN;
  - fenced, header-only;
  - an HTML comment;
  - a lowercase `#### scenario:` label;
  - a step under a `#####`;
- `too-long-501`, `exactly-500`, `two-lines-501`, `comment-counts` and `metadata-only` main specs, and an ADDED delta over 500;
- an ADDED delta with a missing THEN;
- `inline-fence`;
- `unspaced-header`;
- a MODIFIED growth delta against a short base requirement, one against a long base, and a renamed-then-modified one;
- `frontmatter-delta`, `frontmatter-bom-crlf`, `frontmatter-comment-title`, and the non-front-matter lookalikes `indented-dashes`, `thematic-break`, `heading-before-close` and `unclosed`.

`scripts/capture-validator-parity-2x.py` captures `validate-parity-corpus-2x{,-strict}.json` on 1.14.0, 1.14.3, 1.15.0, 1.16.1, 1.17.0, 2.0.0, 2.1.0 and 2.1.1. 1.14.0 and 1.14.3 pin the too-long rule's start.

A new `ValidatorVerdictReleaseParityTest` validates the corpus with the built-in validator following each captured release, and requires every item's verdict to equal that release's, in both modes. The one exception is a pinned list of items the release fails under `--strict` on a warning the plugin leaves to the CLI, the "text above the first `##`" warning: the front-matter deltas from 1.14.3 until 2.1.0, and the lookalike deltas from 1.14.3 on every release. For those the plugin's strict verdict must be the laxer `valid`, and the list must match exactly, so it can neither grow nor shrink silently.

### 7. Wrapped task lines: a count contract, no code

A capture of `openspec instructions apply --change <c> --json` (2.0.0 and 2.1.1) runs over a `tasks.md` with wrapped and hard-broken tasks. `TaskLinesContractTest` asserts that the plugin's `[complete, total]` equals the capture's progress, and that the joined descriptions are the capture's. The second assertion is what a future change would build on, so it is read from the capture and not used by production code.

## Risks / Trade-offs

- **[A user's CLI is older than the target, and the CLI is missing]** With no CLI, the plugin follows the target. Someone who means to use CLI 1.15 but hasn't installed it gets 1.17's step warning. → That's the release a fresh install would bring; and default verdicts are unaffected, only strict ones change.
- **[A requirement-text port measuring differently]** Every length rule would sit one side or the other of upstream's. → The corpus has bodies at 500 and 501 characters, a two-line body, a comment and a metadata-only body, judged by the real CLIs.
- **[The too-long rule reverses the 1.14 choice]** At 1.14 the plugin left this strict-only warning to the CLI. → This change ports two other strict-only warnings, so leaving one out would be inconsistent, and the per-release test needs it to require equality.
- **[Upstream false negative]** A bare uppercase `WHEN` in prose satisfies the step check, so some step-less scenarios pass. → The plugin mirrors it: never stricter. It is recorded internally.

## Migration Plan

Behaviour changes only for the releases that have each rule. No stored data or settings change. Rollback is reverting the change.

## Open Questions

None.
