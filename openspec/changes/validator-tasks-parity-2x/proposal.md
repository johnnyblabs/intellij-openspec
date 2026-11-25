# Proposal

## Why

The built-in validator answers when the OpenSpec CLI is missing, or when its run fails. It is meant to give the CLI's verdict, never a stricter one. Four CLI rules have changed that verdict since the validator was last aligned:

- **Long requirements (CLI 1.14.1).** A main-spec or ADDED requirement whose text is over 500 characters is a warning that fails `--strict`. When 1.14 shipped, the plugin chose to leave that strict-only warning to the CLI. This change ports two more strict-only warnings, so the length rules come too, and the built-in strict verdict then matches each release.
- **Scenario steps (CLI 1.16).** A scenario with no WHEN/GIVEN step, or no THEN step, is a warning that fails `--strict`. The CLI checks main specs and ADDED/MODIFIED deltas alike. The plugin still reports it as an INFO hint, on main specs only, and doesn't count GIVEN. Its spec says the CLI performs no clause-structure validation, which hasn't been true since 1.16.
- **MODIFIED requirements that grow too long (CLI 2.1.1).** A MODIFIED requirement whose text grows past 500 characters, beyond the current main-spec text, gets a strict-failing warning.
- **Delta front matter (CLI 2.1.1).** A delta spec may start with a YAML front-matter block. Before 2.1.1, every release warned that text above the first `##` section is not carried by archive, and `--strict` failed on it. In 2.1.1 the block is ignored, and archive only notes that it stays with the change.

Each was verified on 2026-11-24, by running CLI 1.16.1, 1.17.0, 2.0.0, 2.1.0 and 2.1.1 on the same projects and by reading each release's source. The 1.16.0 and 1.14.1 dates come from those releases' notes. This is tracked in the linked tracker entry for validator and task parity with CLI 2.x, which folds in the entries for the 1.16 step check and the parity-corpus cases.

## What Changes

- **The built-in validator follows a release.** Each rule a release introduced applies only when the plugin is following that release or a later one. The release followed is the detected CLI's version, or the plugin's target release when no CLI is detected. When a detected version can't be read, no release-gated rule applies.
- **Too-long check.** From 1.14.1, a main-spec or ADDED requirement whose text is over 500 characters is a WARNING that fails a strict run. The text is measured as the CLI measures it.
- **Step check.** From 1.16.0, a `Scenario:`-labeled block (label in any case) with a body but no trigger (WHEN or GIVEN) or no outcome (THEN) is a WARNING, in main specs and in ADDED/MODIFIED deltas.
  - It follows upstream's own rule: a bolded keyword in any case (`**When**`, `**GIVEN:**`), or a bare uppercase word anywhere on a body line.
  - `AND` adds nothing by itself. The header line and fenced text are ignored; HTML comments are not.
  - It fails a strict run. Below 1.16 the current INFO hint stays.
- **MODIFIED growth check.** From 2.1.1, a MODIFIED requirement whose text is over 500 characters and longer than the main spec's current text is a WARNING that fails a strict run. A renamed requirement is compared with the one it was renamed from.
- **Delta front matter.** From 2.1.1, a front-matter block at the top of a delta spec is ignored by the built-in validator, under upstream's exact rule for what counts as front matter.
  - The plugin's own merge (Sync Specs, and archive without a CLI or below 1.5) never copies front matter into a main spec, and reads a main spec's Purpose past its own front matter.
  - Below 2.1.1, the CLI's "text above the first `##` section" warning stays the CLI's. The plugin doesn't port it, and is laxer there by design.
- **Release-parity corpus.** New corpus cases are captured from the real CLI, as #595 asks:
  - the step check in a main spec and in a delta, with every step form;
  - an inline triple-backtick span;
  - an unspaced `###Requirement:` header;
  - long and exactly-500 requirements;
  - growing and renamed MODIFIED requirements;
  - deltas with and without real front matter.

  The built-in validator's verdict is held to each captured release's, in default and strict mode, with the plugin following that release. The only exceptions are the documented laxer items.

**Out of scope**, each laxer than the CLI and so allowed:
- the scenario-loss ERROR for a MODIFIED requirement that drops a main-spec scenario;
- the pre-2.1.1 "text above the first `##`" warning;
- 2.1.1's other merge changes: a new spec's `## Purpose` skeleton and the retirement audit. These are merge behaviour, not validation, and belong in a separate tracker entry.

**Upstream false negative**, recorded internally: a bare uppercase `WHEN` in plain prose satisfies the step check.

**Corrections to the tracker entry**, from the real CLIs:
- Delta front matter is 2.1.1's, not 2.x's: 2.0.0 and 2.1.0 still warn about it.
- **Wrapped task lines need no plugin change.** From 2.0.0, `openspec instructions apply` joins a wrapped task line into one description. Task counts are unchanged, because a line is checked for a task before any join. The plugin never reads a task's text: `TaskLines` only counts, and `ApplyPromptBuilder`, `VerificationService` and `ExploreContextService` read or hand over the whole file. A contract test pins the counts on wrapped tasks.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `validation`:
  - **CLI verdict is authoritative when the CLI is available:** the laxest-anchor parity guard follows the anchor release, and release-gated rules are held to each release instead.
  - **Spec format validation:** the step check replaces "the CLI performs no clause-structure validation".
  - **Per-run strict validation:** the strict-failing set depends on the release followed.
  - **New requirements:**
    - which release the built-in validator follows;
    - the too-long check;
    - the step check;
    - the MODIFIED growth check;
    - delta front matter.

## Impact

- **Code:**
  - `BuiltInValidator`: the too-long, step and growth checks, and front matter.
  - `OpenSpecValidateAction`: a release-aware strict-failing set.
  - `SpecMerge`: front matter stays with the change.
  - The editor inspections (`SpecFormatInspection`, `DeltaSpecInspection`) show the new warnings, gated the same way.
- **Tests and fixtures:**
  - a release-parity corpus captured on 1.15.0, 1.16.1, 1.17.0, 2.0.0, 2.1.0 and 2.1.1, with `scripts/capture-cli-generation.py` extended to capture it;
  - a per-release verdict-parity test; the existing 1.8-anchor test keeps its corpus and now follows 1.8.0 explicitly;
  - existing tests that assert the INFO step hint are pinned to a release below 1.16, and every new test names its release, since the target moves at the CLI bump;
  - a task-count contract on wrapped tasks.
- **Platform:** none; IntelliJ 2024.2+ support is unchanged.
- **Docs:** CHANGELOG, the validation section of `docs/feature-reference.md`, and `docs/openspec-support.md`.
