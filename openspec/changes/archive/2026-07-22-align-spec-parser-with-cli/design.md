## Context

`SpecParsingService` parses OpenSpec spec markdown into a `SpecFile` model (title, requirements, scenarios, keyword state) consumed by the spec tree, the list action, and the project service. It uses `Pattern.MULTILINE` regexes with no awareness of code fences and with recognition rules that were inferred rather than matched to the OpenSpec CLI. An audit against the upstream `@fission-ai/openspec` CLI (1.6.0) confirmed four concrete divergences: structural markers are matched inside fenced code blocks; scenarios are recognized by a `Scenario:`-labelled or bold `**Scenario:**` form (the CLI counts *any* `####` header and ignores the bold form); the normative-keyword set is `SHALL/SHOULD/MAY` and omits `MUST` (the CLI's normative set is exactly `SHALL/MUST`); and the keyword is tested over the whole requirement section rather than the requirement body.

A decisive finding shaped this design: **the CLI is not a markdown-AST parser.** It ships no markdown library and recovers all structure with anchored per-line regexes over a hand-built code-fence mask. The spec dialect the plugin must match is therefore *narrower* than CommonMark, not equal to it.

The corrected parser is the keystone of the "Spec Intelligence & Viewing" release: the viewer, deltas view, and tree badge overlays all read this model, so it must match the CLI before those features render anything from it.

## Goals / Non-Goals

**Goals:**
- Recover spec structure (title, requirements, scenarios, normative-keyword state) in parity with the OpenSpec CLI, including code-fence exclusion.
- Prove parity against output captured from the real CLI, not hand-authored shapes.
- Preserve the existing plugin-only enrichment (requirement names, scenario names, clause breakdown) where it does not conflict with CLI parity.
- Keep the change behavior-preserving for the tree/list *mechanism* — only the recovered counts change, because they become correct.

**Non-Goals:**
- No markdown-AST library and no new dependency. `org.commonmark` (already on the classpath for HTML rendering) is not used for spec-structure parsing.
- No unification with `BuiltInValidator`'s separate parse path (its own `maskFences`, used by validation and inspections). That drift risk is accepted here and left as a follow-up.
- No reimplementation of cross-file or stateful CLI operations — delta assembly across a change's `specs/` tree, archive/apply, and spec-name-from-path stay CLI/filesystem-backed.
- No change to validation verdicts or editor inspections.

## Decisions

**Decision 1 — Port the CLI's line-oriented scanner; do not adopt an AST.**
Reimplement parsing as a line scanner mirroring the CLI: build a per-line code-fence mask first, then apply anchored recognition (ATX header at column 0 with mandatory trailing whitespace; requirement = level-3 `Requirement:` header case-insensitively; scenario = any level-4 header; normative = whole-word case-sensitive `SHALL`/`MUST` on the requirement body). *Alternatives considered:* (a) **flexmark** — the original issue's suggestion; rejected because a general CommonMark parser is *more* permissive than the CLI dialect and would over-recognize setext headings, indented-code headings, trailing-hash forms, and fences nested in blockquotes — every such case becomes suppression work, i.e. fighting the tool to make it *less* correct. (b) **The already-present `org.commonmark`** — no new dependency, but the same suppression burden as flexmark. (c) **Platform Markdown PSI** — pulls `com.intellij.*`, making `verifyPlugin` mandatory, for no parity benefit. Porting the ~4 small, readable upstream functions is the highest-parity, lowest-risk path.

**Decision 2 — Keep parsing and validation as separate parse paths for this change.**
This change touches only `SpecParsingService`. `BuiltInValidator` retains its own `maskFences`/parse path. *Alternative considered:* unify them now (retire the duplicate mask, one parse path). Rejected for blast radius — unification makes validation verdicts depend on the rewritten parser and pulls the verdict-parity suite into this change's risk surface. The drift risk is real and documented, so it becomes an explicit follow-up rather than a silent omission.

**Decision 3 — Separate parsing from validation thresholds.**
The parser recovers *structure*; it does not apply the CLI's validation thresholds (Purpose length, requirement-text length, scenario-min-1). The tree/list only needs structure, so those rules stay out of the parser. This keeps the two concerns independently testable and avoids importing validation semantics the consumers don't use.

**Decision 4 — CLI-captured output is the source of truth for parity tests.**
Parity is asserted against `openspec show <spec> --json` (title, requirement count, per-requirement scenario count) captured from the real CLI over the existing committed corpus, anchored by `validate --all --json` for verdicts. The old regex parser is retained *test-only* as a `LegacyRegexSpecParser` to drive a differential test with an explicitly enumerated set of intended divergences, so the behavior changes are auditable rather than accidental.

## Risks / Trade-offs

- **Visible count changes on existing specs** → This is intended (counts become CLI-correct), but could surprise users. Mitigation: call it out in the changelog as a correctness fix; the differential test enumerates exactly which cases change and why.
- **Fence-mask edge cases (tilde fences, indented fences, unclosed fences, info strings)** → Port the CLI's open/close semantics verbatim and cover each in the adversarial corpus (`indented-code`, `table-keyword`, `html-comment-req`, `setext-header`, `nested-list-scenario`).
- **Two parse paths can still drift** (Decision 2) → Accepted for now; recorded as a follow-up change to unify with `BuiltInValidator`.
- **Indented code blocks** are a genuinely new correctness case the old line mask did not handle → The `indented-code` fixture forces coverage so the ported mask handles 4-space code blocks the way the CLI does.
- **Coverage denominator** → The rewrite adds branches; keep `LegacyRegexSpecParser` under `src/test/` so it is not instrumented, and ratchet the JaCoCo floors up post-merge once real coverage is measured.

## Migration Plan

No user migration. Internal steps: (1) land the ported parser behind the existing `SpecParsingService` public surface so consumers are unchanged; (2) capture CLI fixtures and add the contract + differential + CRLF parity tests; (3) flip existing unit assertions that encoded the old buggy behavior to the CLI-correct expectation; (4) after green, re-measure and ratchet JaCoCo floors. Rollback is a straight revert — no schema, dependency, or data changes accompany the change.

## Open Questions

- **Delta-spec surfacing:** if a later viewing feature renders a change's delta specs, it must match the CLI's `parseDeltaSpec` path (ADDED/MODIFIED/REMOVED/RENAMED, non-canonical `###` headers recorded as skipped/INFO). This change parses main specs only; the delta path is deferred to the feature that needs it and is noted so it isn't reimplemented ad hoc.
- **Follow-up sizing:** the `BuiltInValidator` unification (Decision 2) is a separate change — its scope (retire `maskFences`, route validation through the shared parser, keep verdict-parity green) is to be proposed once this lands.
