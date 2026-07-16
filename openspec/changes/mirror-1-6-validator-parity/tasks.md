## 1. Validator semantics (BuiltInValidator)

- [x] 1.1 Add a fence-masking utility (backtick and tilde fences, preserving line positions) and apply it before keyword and scenario matching in both the spec and delta paths
- [x] 1.2 Tighten the requirement-keyword rule to word-boundary `SHALL|MUST` (keep the header-only targeted diagnostic and quick-fix; `SHALL NOT` still satisfies via the `SHALL` word match); update the diagnostic message to name only SHALL/MUST
- [x] 1.3 Emit INFO `delta-skipped-header` for non-canonical level-3 headers inside ADDED/MODIFIED delta sections (two variants: nameless `### Requirement:` → add-a-name hint; other headers → ignored-unless-canonical hint), anchored to the header line; verify INFO never affects the verdict
- [x] 1.4 Confirm the inspection layer renders INFO as a weak/info-tier highlight (map severity if needed)

## 2. Verdict-parity corpus and contract tests

- [x] 2.1 Author the parity corpus as a committed seed (specs + one change with delta files) exercising: SHOULD-only requirement, keyword-in-fence, keyword-on-second-body-line (passes), header-only keyword, scenario-only-in-fence, non-canonical delta header (INFO on a valid file), nameless requirement header, plus clean control cases
- [x] 2.2 Capture the real 1.6.0 CLI's `validate --all --json` over the corpus (isolated env, sanitized paths) into `src/test/resources/fixtures/cli/1.6.0/validate-parity-corpus.json`, and record the corpus + recipe in the fixtures README manifest
- [x] 2.3 Add the verdict-parity contract test: for each corpus case, plugin verdict == captured CLI `valid`; plus targeted unit tests for fence masking and the SHALL/MUST tightening
- [x] 2.4 Update any existing validator tests that encoded the SHOULD/MAY acceptance or fence-blind matching

## 3. Support declaration

- [x] 3.1 Apply the `plugin-core` supported-versions delta (1.3.x–1.6.x) and the `coordination-surfaces` 1.6.x contract row; verify the per-generation contract tests referenced by the 1.6.x row exist (they shipped with the store-health and fixture-sweep changes)
- [x] 3.2 Align `docs/openspec-support.md`'s supported-set statement with the four-line declaration (the 1.6.x line description already exists)

## 4. Docs and verification

- [x] 4.1 CHANGELOG entry (user-facing): validator verdict parity with CLI 1.6 — fence-aware evaluation, SHALL/MUST-only keywords, new advisory hint for skipped delta headers
- [x] 4.2 Run `./gradlew build` (suite + coverage floor) green; ratchet floor only if coverage meaningfully rose
- [x] 4.3 `openspec validate --all` clean; re-validate this change
