## 1. Evidence

- [x] 1.1 Capture with `scripts/capture-ai-tools-fixtures.py` (new `home-file` step):
  - `update-change-skill-only` on 1.5.0 and 1.6.0;
  - `skill-dir-without-skill-file` on 1.7.0, 1.17.0 and 2.2.0;
  - `skill-name-not-openspecs` on 1.17.0 and 2.2.0;
  - `router-skill-only` on 1.17.0, 2.1.1 and 2.2.0;
  - `router-skill-shared-only` on 2.1.1 and 2.2.0;
  - `router-skill-minimax-home` on 1.8.0 and 1.17.0;
  - `claude-folder-with-home-skills` on 1.17.0, 2.1.1 and 2.2.0.

## 2. The rule

- [ ] 2.1 `AiToolRegistry.skillNames(release)` (D1), with unit tests at 1.5.x/1.6.0 and 2.1.x/2.2.0, and contract-tested against each release's upstream list.
- [ ] 2.2 The set-up verdict's skill check requires `<name>/SKILL.md` for the release's names (D2).
- [ ] 2.3 1.x detection's name uses take the release's names (D2).
- [ ] 2.4 The located-skill fallback, per the D3 ruling.
- [ ] 2.5 `ConfiguredToolsContractTest` (with 1.5.0) and `AiToolDetectionContractTest` pass on every captured release.
- [ ] 2.6 Mutation checks: each must fail a test.
  - accept a folder without `SKILL.md`;
  - use the newest names on 1.5;
  - use the newest names on 2.1;
  - use the newest names in 1.x home detection.

## 3. Docs and verification

- [ ] 3.1 Fixtures README (the new cases), CHANGELOG (Fixed).
- [ ] 3.2 `./gradlew build`, `openspec validate --all --strict`.
