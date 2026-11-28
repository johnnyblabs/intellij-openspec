## 1. Evidence

- [x] 1.1 Capture the boundary cases (`scripts/capture-ai-tools-fixtures.py`, `CAPTURE_VERSIONS`/`CAPTURE_CASES`):
  - `kimi-dir-only` on 1.3.1, 1.4.0, 1.6.0 and 1.7.0;
  - `devin-dir-only` and `kimi-code-dir-only` on 1.6.0 and 1.7.0;
  - `codex-config-only` on 1.7.0, 1.8.0 and 1.17.0;
  - `agents-empty-dir` on 1.7.0 and 1.8.0, and `agents-unmarked-generic` and `minimax-global` on 1.8.0;
  - `builtin-add-codex-list-on-empty` on 1.8.0;
  - `zed-dir-beside-codex` and `agents-workflows-only` on 1.10.0 and 1.11.0;
  - `amp-dir-only` and `gsd-dir-only` on 1.13.2 and 1.14.0;
  - `aider-desk-skills` on 1.7.0, 1.8.0 and 1.17.0.

## 2. Detection

- [ ] 2.1 `AiToolRegistry.asOf1x(tool, release)` (D2), with the registry contract test extended to every captured 1.x dump and to older directories.
- [ ] 2.2 `detect1x` takes the release: accepted tools only, their D2 shapes, and the D3 boundaries. `AiToolDetectionService` passes the detected CLI's version; other callers pass `null` (today's behavior: 1.17.0 shapes and rules, no release filter).
- [ ] 2.3 `AiToolDetectionContractTest` over every captured release with the release passed, with case counts per release; remove the `detection-1x-release-rules` known-gap entry.
- [ ] 2.4 Mutation checks: drop the 1.8.0 gate, drop the 1.11.0 gate, drop the release filter, use 1.17 shapes on 1.7.0. Each must fail a test.

## 3. Docs and verification

- [ ] 3.1 Fixtures README (the new detection captures and what they show), CHANGELOG (Fixed).
- [ ] 3.2 `./gradlew build`, `openspec validate --all --strict`.
