## 1. Fixtures — capture CLI truth before touching the parser

- [ ] 1.1 Add adversarial spec dirs under `src/test/resources/fixtures/cli/1.6.0/parity-corpus/openspec/specs/`: `indented-code` (SHALL + `####` header inside a 4-space code block), `setext-header` (`===`/`---` underlined requirement-like line), `table-keyword` (`SHALL` / pipe-delimited `Scenario:` in a table), `html-comment-req` (`<!-- ### Requirement: X -->`), `nested-list-scenario` (nested lists under a scenario). Include at least one spec exercising a `####` non-`Scenario:` header and one with a bold `**Scenario:**` line.
- [ ] 1.2 Capture `openspec show <spec> --json` for every corpus spec into `src/test/resources/fixtures/cli/1.6.0/spec-structure/<id>.show.json`, using an isolated `HOME`/`XDG_*` env over the committed corpus, sanitizing `root.path`. Record the capturing CLI version.
- [ ] 1.3 Re-capture `validate-parity-corpus.json` so it covers the new adversarial specs.
- [ ] 1.4 Add a `spec-structure/` row to `src/test/resources/fixtures/cli/README.md` manifest (recapturable per CLI generation; note `root.path` is the only sanitized field).

## 2. Parser reimplementation — line scanner in parity with the CLI

- [x] 2.1 Port the code-fence mask: a per-line boolean mask; fence opens on a 3+ backtick/tilde run (optionally indented, info string allowed) and closes on a matching marker char with run length ≥ the opening; fence delimiter lines are masked. Apply it before every structural match.
- [x] 2.2 Recognize requirements from non-fenced ATX level-3 `Requirement:` headers (case-insensitive on the token, hash run at column 0 + mandatory whitespace); ignore setext, indented, and trailing-hash forms.
- [x] 2.3 Recognize scenarios as any non-fenced ATX level-4 header; drop `Scenario:`-label requirement and drop bold `**Scenario:**` recognition.
- [x] 2.4 Classify normative keywords as whole-word case-sensitive `SHALL`/`MUST` evaluated on the requirement **body** (non-fenced, non-blank lines between the header and the first scenario / next requirement); stop treating `SHOULD`/`MAY` as normative and stop testing over scenarios or the header line.
- [x] 2.5 Retire or replace the incorrect `SpecPatterns` scenario/keyword patterns; leave the case-insensitive requirement-header pattern (already correct).
- [x] 2.6 Add value equality (or a projected comparison tuple) to `SpecFile`/`Requirement`/`Scenario` to support parity assertions.

## 3. Consumers — keep behavior-preserving

- [x] 3.1 Update `SpecTreeModel`, `OpenSpecListAction`, `OpenSpecProjectService` only as needed so they compile and render against the corrected model; no behavior change beyond the now-correct counts.
- [x] 3.2 Confirm `SpecParsingService` stays a `projectService` and that no CLI/I/O runs on the EDT (parsing is pure-string/VFS-read; `parseAllSpecs`/`parseSpec(VirtualFile)` stay off the EDT where they already are).

## 4. Tests — contract-first, differential, invariance

- [ ] 4.1 `SpecParserCliStructureContractTest`: parse each corpus spec through the new parser; assert title / requirement count / per-requirement scenario count equal the captured `spec-structure/<id>.show.json`. Include a corpus-drift guard (fixture count == `.show.json` count == corpus dir count). This test must fail on the pre-fix parser and pass after.
- [ ] 4.2 `SpecParserRegressionParityTest`: copy the current `parseSpecContent` verbatim into a test-only `legacy/LegacyRegexSpecParser`; assert new-vs-legacy agree on `{title, requirement names, keywords, scenario names, clauses}` except an explicitly enumerated `KNOWN_DIVERGENCE` set (fenced scenario/keyword, header-only keyword, bold-`**Scenario:**`, `MUST` keyword), each entry naming the spec and the CLI-correct expectation.
- [ ] 4.3 `SpecParserCrlfLfParityTest` modeled on `CrlfLfParseParityTest`: parse each corpus spec as LF, CRLF, and trailing-lone-CR; assert identical recovered structure.
- [x] 4.4 Extend `SpecParsingServiceTest`: keep the name/keyword/clause enrichment assertions as the regression net; add deterministic adversarial cases; flip any assertion that encoded old buggy behavior to the CLI-correct expectation with a comment tying it to this change.
- [ ] 4.5 Add one VFS-path test (via `SpecParsingIntegrationTest`) exercising `parseAllSpecs`/`parseSpec(VirtualFile)`, and assert `SpecTreeModel` renders the CLI-correct counts for at least one corpus spec.

## 5. Coverage, docs, and close-out

- [ ] 5.1 Keep `LegacyRegexSpecParser` under `src/test/` so it is not instrumented; after the suite is green, run `jacocoTestReport`, read the new numbers, and ratchet the JaCoCo floors in `build.gradle.kts` up to just below current coverage.
- [ ] 5.2 Update internal docs that describe parse behavior (`docs/feature-reference.md`, any `scripts/docs/`/wiki page on spec parsing) to state the CLI-parity recognition rules (fence exclusion, any-`####` scenario, `SHALL`/`MUST` on the body).
- [ ] 5.3 Update `README.md` / `CHANGELOG.md` `## Unreleased` if the corrected tree/list counts are user-visible — framed as a correctness fix (counts now match `openspec validate`/`show`), vendor-neutral, no tracker identifiers.
- [ ] 5.4 Run `./gradlew build` (suite + coverage floor) green; confirm `verifyPlugin` is not required (no new `com.intellij.*` reference, no new dependency) — if the diff unexpectedly adds one, the pre-push hook will run it.
