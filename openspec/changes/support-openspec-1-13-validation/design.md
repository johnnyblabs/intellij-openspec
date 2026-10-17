## Context

See proposal.md — Why. The facts below were checked against the real CLI: the 1.12.0 and 1.13.2 binaries (and, for forward-compatibility checks, 1.14.1 and 1.14.3), run with isolated `HOME`/`XDG_*`, plus source diffs of the published tarballs. Each control below is a small project run through the real CLI.

**Corpus result.** Over the shared parity corpus, 1.13.2 gives byte-identical verdicts to 1.12.0: default 12/13 valid, strict 9/13. The version-stability guard's anchors (default `1.8.0`, strict `1.6.0`) therefore hold unchanged.

**RENAMED grammar.** The client's parser in 1.13.0 is ``^\s*[-*+]?\s*FROM:\s*`?###\s*Requirement:\s*(.+?)`?\s*$`` (and the same for `TO:`). In 1.12 it was `-?`. On a `*`-bulleted RENAMED control, 1.12 reports `valid:false` and 1.13.2 reports `valid:true`. The plugin uses the same `^\s*(?:-\s*)?FROM:…` pattern in three places: `BuiltInValidator.RENAMED_ENTRY_PATTERN`, `DeltaSpecInspection.RENAMED_ENTRY_PATTERN` and `SpecSyncService.RENAMED_ENTRY`. It is now stricter than the client.

**Plugin vs client RENAMED forms (pre-existing divergence).** The client *requires* `### Requirement:` inside the FROM/TO line. The plugin also accepts bare names (`FROM: Old name`), and the tests and scaffolding template use that form. For validation, accepting more is laxer, which is allowed. For **sync**, however, the plugin keeps the whole remainder of the line as the name. A canonical ``FROM: `### Requirement: Old` `` therefore becomes the name `` `### Requirement: Old` ``, and `applyRenamed` never finds it.

**REMOVED grammar.** The client accepts plain `### Requirement:` blocks and bullet entries, using ``^\s*[-*+]\s*`?###\s*Requirement:\s*(.+?)`?\s*$``. `SpecSyncService.parseRequirementBlocks` only matches the line-anchored `^###` header, so bullet entries produce no operation at all. The built-in validator never flagged bullet REMOVED, so validation parity already holds there.

**Task grammar.** The client's 1.13.1 `TASK_LINE_PATTERN` is `^\s*(?:[-*+]|\d{1,9}[.)])\s*\[(?:\s*([^\]\s]?)\s*\](?![([])|\s+\])\s*(.*)`, and a task is done iff its mark lowercased is `x`. Two plugin implementations count tasks:
- `ApplyPromptBuilder.countTasks` recognizes only `- [x]`, `- [X]` and `- [ ]` (prefix match on the trimmed line). It feeds the tree node `X/Y`, `WorkflowActionPanel` and `OpenSpecApplyAction`.
- `VerificationService` has three patterns: incomplete `-\s*\[\s*]`, complete `-\s*\[x]` (case-insensitive) and in-progress `-\s*\[~]`.

On the task-markers control (`- [x]`, `1. [ ]`, `+ [ ]`, `- [~]`, `- []`, `* [ ]`), the client reports `completedTasks:1, totalTasks:6`. `countTasks` gives 1/1. The tree-view spec already says task counts are "as reported by the OpenSpec CLI", so the plugin currently falls short of an existing requirement.

**Error response.** In 1.13.1, `validate <id with / or ..> --type …` returns `{"status":[{"severity":"error","code":"invalid_item","message":…}]}` with exit 1. `OpenSpecValidateAction` routes any `{`-prefixed stdout to `CliOutputParser.parseJsonOutput`. With no `items`, that leaves `allValid = true` and returns a PASS. The fallback to the built-in validator only happens on an exception.

## Goals / Non-Goals

**Goals:**
- Wherever 1.13 made the client laxer, the fallback, the inspection and sync match it.
- The plugin's task counts equal the client's counts for the same `tasks.md`.
- No PASS on an error response.
- Every mirrored behavior is pinned by a positive control captured from the real 1.13.2 CLI.

**Non-Goals:**
- Adopting 1.13.1's new verdict-affecting ERRORs, or its new strict-only WARNINGs (an orphan requirement under a non-delta section, a tasks file with no checkbox). The fallback stays laxer, which is allowed.
- Any 1.14.x work, including the strict-arm redesign that 1.14.2's corpus flip forces. That is the next release.
- Reworking the scaffolding delta template. It writes `## ADDED`, `## REMOVED` and similar without `Requirements`, and teaches bare-name RENAMED. That pre-existing off-model template issue is handled by its own change in the same release, `fix-delta-spec-template-headings`, and is not changed here.
- General hardening of JSON-syntax-error handling in `parseJsonOutput`. A malformed `{…` payload is also read as PASS today. Only the documented `status` error response is in scope.

## Decisions

**1. One shared RENAMED pattern, widened to `(?:[-*+]\s*)?`.** Replace the three copies with a single constant, for example in `SpecPatterns`, that `BuiltInValidator`, `DeltaSpecInspection` and `SpecSyncService` all reference. This keeps the three readers from drifting apart again; drift is the root cause of this bug. The pattern keeps accepting bare names, so validation stays laxer than the client. *Alternative considered:* adopt the client's exact pattern, which requires `### Requirement:`. Rejected, because it would newly reject the bare-name form the plugin's own template teaches, making the fallback stricter for existing users.

**2. Normalize RENAMED names in sync, not in validation.** After matching, strip an optional surrounding backtick pair and a leading `###\s*Requirement:\s*` (case-insensitive token, consistent with `SpecPatterns.REQUIREMENT_HEADER_PREFIX_REGEX`) from both captured names. Bare names pass through unchanged. *Alternative considered:* match two grammars separately. Rejected, because a single normalize step is simpler and is exactly what the client does internally.

**3. Bullet REMOVED becomes a header-only operation.** In `SpecSyncService`'s REMOVED handling, also scan for ``^\s*[-*+]\s*`?###\s*Requirement:\s*(.+?)`?\s*$`` lines and emit a REMOVED `DeltaSpecOperation` per match, with the stripped name and no block body. `applyRemoved` already works by name, so the downstream apply logic needs no change. Order is preserved: block entries and bullet entries are emitted in document order, as in the client.

**4. One task-line parser shared by every counter.** Add a small utility, for example `TaskLines`, that ports the client's `TASK_LINE_PATTERN` and its done rule. It returns per-line `{mark, done}` plus counts.
- `ApplyPromptBuilder.countTasks` delegates to it. The method signature stays the same, so the tree, panel and Apply callers are untouched.
- `VerificationService` derives its incomplete, complete and in-progress buckets from it. In-progress means mark `~`; any other non-`x` mark is incomplete. This preserves the spec's distinct in-progress reporting.

*Alternative considered:* source counts from `openspec list --json` (`completedTasks`/`totalTasks`). Rejected for this change, because Verify and the tree must work CLI-absent. A faithful local port gives the same numbers without a CLI round-trip. The contract test pins the port against the client's captured counts, so drift is caught.

**5. The error response surfaces as a failed run through the existing fallback path.** `parseJsonOutput` detects "object, no `items` array, top-level `status` array containing an entry with `severity` equal to `error` (case-insensitive)". It then throws a dedicated unchecked exception, for example `CliErrorResponseException`, carrying the first error message. `OpenSpecValidateAction` already catches `Exception` around the CLI path and falls back to the built-in validator with the strict flip applied, so no new branching is needed there. *Alternative considered:* return a failing `ValidationResult`. Rejected, because that would make the CLI the verdict owner for a run the CLI itself rejected. The spec requires the built-in fallback, and an exception reuses the one tested fallback route.

**6. Fixtures: the 1.13.2 capture is the contract.** Capture with an isolated `HOME`/`XDG_*` and `OPENSPEC_TELEMETRY=0`, sanitize roots to `/fixture`, and commit under `src/test/resources/fixtures/cli/1.13.2/`:
- the parity twins and `version.txt`
- `renamed-star` and `removed-star` change validation (both `valid:true`)
- `show --json` for `removed-star`, the client's own reading of the bullet REMOVED
- `list --json` for `task-markers` (`completedTasks:1, totalTasks:6`), plus the control's `tasks.md`
- the `invalid_item` response

Contract tests parse the fixture inputs through the plugin and assert the plugin's result equals the client's captured result. They do not compare against hand-written shapes.

## Risks / Trade-offs

- **Risk: a vacuous RENAMED lock.** A test that only exercises `-` bullets passes with or without the fix. → **Mitigation:** the positive control is the `*`-bulleted capture, and the test must fail against the old `(?:-\s*)?` pattern. Verify this by temporarily reverting during apply.
- **Risk: counting more lines changes visible task progress for existing users.** Changes using `*` or numbered checkboxes will show larger totals. → **Mitigation:** the new numbers are the client's numbers, and the tree-view spec already requires CLI-faithful counts. Call it out in the CHANGELOG as a fix.
- **Risk: the client counts checkboxes inside code fences and HTML comments.** The port inherits this deliberately; the client documents it as the safer false positive. → **Mitigation:** port faithfully and pin it with a fenced-checkbox unit test. Do not "improve" on the client, because then counts would diverge again.
- **Risk: a 1.14 regression after this ships.** 1.14.2 flips one corpus item under `--strict`, which would break the strict arm's map equality. → **Mitigation:** this change commits only 1.13.2 twins, which are byte-identical. The strict-arm redesign is explicitly next-release work.
- **Risk: the error-response exception masks real CLI output.** → **Mitigation:** detection requires *no* `items` and an explicit error-severity `status` entry. Any response with `items` keeps the existing path, and that path is pinned by the existing contract tests.

## Migration Plan

This change is additive. There is no data or config migration. Rollback means reverting the source changes, fixtures, guard edits, target-version bump and docs.
