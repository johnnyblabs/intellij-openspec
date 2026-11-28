## 1. Evidence

- [x] 1.1 Capture the boundary cases (`scripts/capture-ai-tools-fixtures.py`, `CAPTURE_VERSIONS`/`CAPTURE_CASES`):
  - `kimi-dir-only` on 1.3.1, 1.4.0, 1.6.0 and 1.7.0;
  - `devin-dir-only` and `kimi-code-dir-only` on 1.6.0 and 1.7.0;
  - `codex-config-only` on 1.7.0, 1.8.0 and 1.17.0;
  - `agents-empty-dir` on 1.7.0 and 1.8.0, and `agents-unmarked-generic` and `minimax-global` on 1.8.0;
  - `builtin-add-codex-list-on-empty` on 1.8.0;
  - `zed-dir-beside-codex` and `agents-workflows-only` on 1.10.0 and 1.11.0;
  - `amp-dir-only` and `gsd-dir-only` on 1.13.2 and 1.14.0;
  - `aider-desk-skills` on 1.7.0, 1.8.0 and 1.17.0;
  - `agents-generic-skill-by-hand` (a generic OpenSpec skill under `.agents/skills`, no init) on 1.7.0 and 1.8.0, which pins the `agents` shape change;
  - `amp-files-from-1.14` (Amp's files as 1.14.3 writes them) on 1.13.2, which has no Amp.

## 2. Detection

- [x] 2.1 `AiToolRegistry.asOf1x(tool, release)` (D2).
  - Extend the registry contract test to every captured 1.x dump: the exact dump list, each dump's tool count, and every tool compared. `windsurf` is read as `devin`. Compare older directories too.
  - Keep the name and order checks from 1.17.0.
  - Unit tests at each shape's boundary (1.6.9/1.7.0, 1.7.5/1.8.0, 1.10.9/1.11.0); `null` returns the record unchanged.
- [x] 2.2 `detect1x` takes the release: accepted tools only, their D2 shapes, and the D3 boundaries. `AiToolDetectionService` passes the detected CLI's version; other callers pass `null` (today's behavior: 1.17.0 shapes and rules, no release filter).
- [x] 2.3 `AiToolDetectionContractTest` over every captured release with the release passed, with case counts per release; remove the `detection-1x-release-rules` known-gap entry.
- [x] 2.3a The no-release path keeps today's behavior. With V1 and `null`:
  - `.aider-desk/skills` is detected;
  - a bare `.codex/config.toml` gives nothing;
  - a marker naming codex gives codex.

  Also: `InstallToolTicks` still ticks AiderDesk, and the service passes the CLI's version (1.7.0 with `.codex/config.toml` gives Codex).
- [x] 2.4 Mutation checks: each must fail a test.
  - drop the 1.11.0 gate;
  - drop the release filter;
  - use 1.17 shapes on 1.7.0;
  - compare releases as text;
  - treat `null` as 1.17.0.
- [x] 2.5 The set-up verdict follows the release (design D2a), pinned by `agents-generic-skill-by-hand`, `amp-files-from-1.14`, `claude-commands-only` and `antigravity-legacy-workflows-only`. It covers:
  - the release filter, D2 shapes, and a release-aware shared-root owner;
  - commands from 1.7.0, and older-folder commands from 1.17.0.

  `ConfiguredToolsContractTest` replays every release with captures (14).

## 3. Docs and verification

- [x] 3.1 Fixtures README (the new detection captures and what they show), CHANGELOG (Fixed).
- [x] 3.2 `./gradlew build`, `openspec validate --all --strict`.
