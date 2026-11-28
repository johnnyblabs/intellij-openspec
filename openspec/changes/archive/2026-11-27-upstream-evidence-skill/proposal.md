# Proposal

## Why

The project subagents that answer "what does upstream do?" (`openspec-guru`) and plan or audit tests (`test-engineer`) each start from an empty context. Every brief has had to re-state the same background, at the cost of tokens, and with a risk of drift when a brief gets it wrong:
- where to read a given CLI release's source (`npm root -g` finds only the installed one);
- that `npx` is off-limits for this;
- the captured-fixture layout and its index;
- the case-table re-capture procedure;
- the `@CliFixtureSuite` rule.

Claude Code lets a subagent preload a skill through its `skills:` frontmatter, so that background can live in one place. Tracked in the linked tracker entry.

## What Changes

- **A new project skill, `upstream-evidence`** (`.claude/skills/upstream-evidence/SKILL.md`), vendor-neutral and tracked. It covers:
  - how to get any release's installed package through the shared capture cache (`scripts/lib/openspec_capture.py`);
  - the `dist/` files that answer common questions;
  - the fixture layout and its README index;
  - how to add a capture case;
  - how replaying tests are tagged and made non-vacuous.
- **`openspec-guru` and `test-engineer` preload it** (`skills: [upstream-evidence]`). openspec-guru's source-reading step points to it for releases other than the installed one.
- Nothing else moves: both agents keep their own discipline and judgment, and the skill holds only the shared mechanics.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None: this changes agent tooling, not plugin behavior (`skip_specs: true`).

## Impact

- **Files:** `.claude/skills/upstream-evidence/SKILL.md` (new), `.claude/agents/openspec-guru.md`, `.claude/agents/test-engineer.md`.
- **Not affected:** plugin code, specs, the IntelliJ 2024.2+ support range, CI (`.claude/**` is a bypass path).
- **Not affected either:** the gitignored tracker and release skills, and the `openspec`-managed `openspec-*` skills.
