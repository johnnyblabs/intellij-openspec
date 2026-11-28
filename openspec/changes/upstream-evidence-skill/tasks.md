## 1. Skill and agents

- [ ] 1.1 Add `.claude/skills/upstream-evidence/SKILL.md`. Its commands must actually work: the package lookup prints a real path, and the `dist/` files it names exist on 1.8.0 and 2.2.0.
- [ ] 1.2 Preload it in `openspec-guru` and `test-engineer` (`skills:` frontmatter), and point openspec-guru's source-reading step at it.

## 2. Verification

- [ ] 2.1 A fresh `openspec-guru` and `test-engineer`, each given a question that needs the skill's facts but a brief without them, answer from the skill: they find a non-installed release's `dist/` and the fixture index without being told the paths.
- [ ] 2.2 Leak check on the tracked files (no homelab identifiers), and `openspec validate --all --strict`.
