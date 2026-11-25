# Tasks

## 1. Corpus and captures

- [x] 1.1 Author the 2.x parity corpus under `src/test/resources/fixtures/cli/2.1.1/parity-corpus-2x/` (design decision 6).
  - The cases are listed in design decision 6:
    - step forms, one scenario per form;
    - the length bodies: 500, 501, two lines, a comment, metadata only, and an ADDED over 500;
    - growth: short, equal and renamed;
    - front matter and its lookalikes;
    - inline fence and unspaced header;
    - a short and a long `base`.
  - Capture with a dedicated `scripts/capture-validator-parity-2x.py` (rather than `scripts/capture-cli-generation.py`, which would also write its other families for releases it never captured). It writes `validate-parity-corpus-2x.json` and `validate-parity-corpus-2x-strict.json` on 1.14.0, 1.14.3, 1.15.0, 1.16.1, 1.17.0, 2.0.0, 2.1.0 and 2.1.1. It installs each release into the scratch prefix, and the captures are sanitized as its other captures are. Document them in `fixtures/cli/README.md`.
  - Verify: the captures reproduce the design's facts:
    - the length items warn from 1.14.1, with 500 passing and 501 warning;
    - the step items warn from 1.16.1, with each form counting or not as the design says;
    - growth warns only on 2.1.1, including the rename;
    - `frontmatter-delta` warns until 2.1.0 and not on 2.1.1, while each lookalike still warns on 2.1.1;
    - `unspaced-header` is invalid everywhere, and `inline-fence` valid everywhere;
    - `DocumentationHygieneTest` passes.
- [x] 1.2 Capture `openspec instructions apply --change <c> --json` on 2.0.0 and 2.1.1 over a `tasks.md` with a wrapped task, a hard line break (`\`), a task followed by a note bullet, and an unwrapped task, into `<v>/instructions/instructions-apply-wrapped.json`. Verify: the capture's descriptions are joined and its counts equal the checkbox count.

## 2. The release followed

- [x] 2.1 Add `ValidationRelease.followed(project)`: the detected CLI's version, else `PluginVersions.targetCliVersion()`. Thread it into `BuiltInValidator`, with the existing entry points looking it up.
  - Verify:
    - a light test: a fake detection at 1.15.0 gives `1.15.0`, and no CLI gives the target;
    - an unparseable version applies no gated rule;
    - pin `BuiltInValidatorRulesTest` and `BuiltInValidatorTest`'s INFO-hint cases to 1.15.0, and `ValidatorVerdictParityTest` to 1.8.0;
    - the existing validator, inspection and parity tests then pass.
- [x] 2.2 Replace the fixed `CLI_MIRRORING_STRICT_WARNINGS` with `strictWarnings(release)` (design decision 3), and pass the release into `applyStrictFallbackVerdict`.
  - Verify with `OpenSpecValidateStrictTest` (each gated rule enters the set exactly where that release fails the corpus item isolating it under `--strict`):
    - 1.14.0 gives the keyword rules only;
    - 1.14.1 adds `spec-requirement-too-long`;
    - 1.16.0 and 2.1.0 add `spec-scenario-steps`;
    - 2.1.1 adds `delta-modified-too-long`;
    - every existing strict-fallback test passes.

## 3. The rules

- [x] 3.1 Port the step check (design decision 2), with `spec-scenario-steps` for main specs and ADDED/MODIFIED deltas from 1.16.0, and the existing INFO below.
  - Verify with a plain test over the corpus files: each step form counts or doesn't exactly as the 2.1.1 capture says.
  - Mutations, each turning a case red:
    - counting `AND`;
    - dropping fence masking;
    - accepting lowercase bare words;
    - checking unlabeled or bodiless scenarios.
- [x] 3.2 Port the requirement-text measure, and add the too-long and growth checks (design decision 4).
  - Verify with a plain test of the measure: every length body in the corpus measures as its capture implies (500 passes, 501 warns, two lines, comment, metadata only).
  - Verify the too-long check: the main-spec and ADDED cases warn following 1.14.1 and not following 1.14.0.
  - Verify the growth check:
    - `grow-short` warns following 2.1.1 and not following 2.1.0;
    - `grow-equal` stays silent;
    - the rename case warns.
  - Mutations, each turning a case red:
    - counting metadata lines;
    - stopping at the first scenario instead of the first heading;
    - `>=` instead of `>`;
    - dropping the "longer than current" clause;
    - ignoring renames.
- [x] 3.3 Port `findFrontmatterEnd` and blank front matter in delta parsing from 2.1.1, and in `SpecMerge` always (design decision 5).
  - Verify with a plain test over upstream's cases:
    - a key-led block, a quoted key, an empty block, and `# Title` then a key all count;
    - a BOM and CRLF are accepted;
    - these don't count: comments with no key, an indented first line, a `##` heading or requirement header before the close, and an unclosed block.
  - Verify with `SpecMerge` tests (Sync Specs' path):
    - merging `frontmatter-delta` leaves no front-matter line in the main spec;
    - a main spec with front matter keeps its Purpose read past it.

## 4. Parity per release

- [x] 4.1 Add `ValidatorVerdictReleaseParityTest` (design decision 6): for each captured release, the built-in validator following it agrees with that release's `valid` flag on every `parity-corpus-2x` item, in default and in strict mode. The existing 1.8-oracle `ValidatorVerdictParityTest` keeps passing.
  - The pinned laxer exceptions are the items a release fails under `--strict` only through its "not carried into the main spec by archive" warnings ("Text above the first `##` section", "Section `## Notes`"), which the plugin leaves to the CLI: front matter from 1.14.3 until 2.1.0, and the lookalikes from 1.14.3 on every release. The plugin must give `valid` for them, and the list must match exactly.
  - The test also found that the plugin's `#### Scenario:` match was case-sensitive, which made it stricter than every release on `#### scenario:`. The label now matches in any case.
  - Verify: the test passes, and checks all 45 items for ten releases (1.14.0, 1.14.1, 1.14.3, 1.15.0, 1.16.0, 1.16.1, 1.17.0, 2.0.0, 2.1.0, 2.1.1) in two modes.
  - Mutations, each turning its releases red:
    - removing the 1.14.1 gate (1.14.0);
    - removing the 1.16 gate (1.15.0);
    - removing the 2.1.1 gate (2.0.0 and 2.1.0);
    - leaving a rule out of the strict set;
    - adding an item to the exceptions.
- [x] 4.2 Add `TaskLinesContractTest` over the 1.2 captures: the plugin's `[complete, total]` equals each capture's, and the capture's joined descriptions are pinned. Verify: the test passes, and appending a line to a wrapped task in the fixture changes no count.

## 5. Docs and finish

- [ ] 5.1 Update the docs:
  - CHANGELOG `## Unreleased`: the step check, the growth check and delta front matter, following your CLI's release;
  - `docs/feature-reference.md`: validation, including that the built-in validator follows the detected or target CLI release;
  - `docs/openspec-support.md`, where it describes validation parity for 1.16 and 2.x.
  - Verify: `DocumentationHygieneTest`, and the vendor-neutral grep.
- [ ] 5.2 Final checks:
  - `./gradlew build`;
  - `./gradlew verifyPlugin`;
  - `openspec validate --all --strict`;
  - a full `uiSmoke` under `caffeinate -dimsu`;
  - test-engineer AUDIT and intellij-code-reviewer on the diff, with each finding fixed or recorded here.
  - Verify: all pass, and the outcome is recorded.

## Workflow follow-up

- File a tracker entry for 2.1.1's other merge changes (a new spec's `## Purpose` skeleton, the retirement audit), and record internally the upstream false negative (a bare `WHEN` in prose satisfies the step check).

- Merge by rebase, archive with spec sync, close the tracker entries, including the two folded in.
