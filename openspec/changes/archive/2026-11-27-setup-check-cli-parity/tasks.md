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

- [x] 2.1 `AiToolRegistry.skillNames(release)` (D1), with unit tests at 1.5.x/1.6.0 and 2.1.x/2.2.0, and contract-tested against each release's upstream list.
- [x] 2.2 The set-up verdict's skill check requires `<name>/SKILL.md` for the release's names (D2).
- [x] 2.3 1.x detection's name uses take the release's names (D2).
- [x] 2.4 A machine-wide install (D3).
  - Remove the fallback.
  - Add `ToolInfo.installsForMachine` (`resolveSkillScope` for one tool, with `SavedSkillsScope`) and `supportsGlobalSkills`, contract-tested against `machine-wide/` on 2.1.0–2.2.0, including the `project-commands-only` and `saved-scope-project` cases.
  - Manage AI Tools gets the group, caption and row note, and Configure's tooltip says what it does.
- [x] 2.4a Count adapter-backed tools' older-folder skills from 1.11.0 (D4), pinned by `adapterBackedWithLegacy`.
- [x] 2.4b Capture machine-wide runs: `scripts/capture-machine-wide.py` on 2.1.0, 2.1.1 and 2.2.0.
- [x] 2.5 `ConfiguredToolsContractTest` (with 1.5.0) and `AiToolDetectionContractTest` pass on every captured release.
- [x] 2.6 Mutation checks: each must fail a test.
  - accept a folder without `SKILL.md`;
  - use the newest names on 1.5;
  - use the newest names on 2.1;
  - use the newest names in 1.x home detection;
  - keep a fallback;
  - ignore global support;
  - ignore the project's own skills;
  - ignore a saved project scope;
  - Configure ignores the flag (runs the project install);
  - drop the adapter-backed rule.

## 3. Docs and verification

- [x] 3.1 Fixtures README (the new cases), CHANGELOG (Fixed).
- [x] 3.2 `./gradlew build`, `openspec validate --all --strict`.
