## 1. Explore skill lookup

- [x] 1.1 Prepend `.claude/skills/openspec-explore/SKILL.md` to `ExplorePromptService.SKILL_FILE_PATHS` (legacy paths retained as fallbacks)
- [x] 1.2 Add `ExplorePromptServiceTest` cases: skills-era path wins when both exist; its 1.6-style frontmatter is stripped

## 2. Tracked skill regeneration

- [x] 2.1 Run the real 1.6.0 `openspec update`; verify the tracked set is six `openspec-*` dirs (new `openspec-update-change`) with `allowed-tools`/`generatedBy` stamps, custom-named skills untouched, and gitignored mirrors excluded from the commit
- [x] 2.2 Confirm no plugin doc enumerates the five-skill set (audit result: none found — record here)

## 3. Sanity checks and verification

- [x] 3.1 Record the value-level 1.6 checks: show/change-show multi-line `text` has no plugin consumer; task-progress is unchanged for single-`tasks.md` changes
- [x] 3.2 CHANGELOG entry (user-facing): Explore honors project skill customizations on skills-era projects
- [x] 3.3 `./gradlew build` green; `openspec validate --all` clean
