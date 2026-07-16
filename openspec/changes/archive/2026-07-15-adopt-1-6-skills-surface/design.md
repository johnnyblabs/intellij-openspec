## Context

Since the CLI's 1.5.0 skills-only migration, agent instructions live at `.claude/skills/openspec-<name>/SKILL.md` (tracked, CLI-regenerated); the pre-1.5 `.claude/commands/opsx/*.md` files are removed by `openspec update`'s legacy cleanup. `ExplorePromptService.SKILL_FILE_PATHS` predates the migration and only lists pre-1.5 paths, so on 1.5+/1.6 projects the lookup always misses and the built-in default is used — project customizations to the explore skill are ignored. At 1.6.0 the tracked skill set also grows to six (`openspec-update-change`) and every `SKILL.md` carries `allowed-tools`/`generatedBy` frontmatter.

## Goals / Non-Goals

**Goals:**
- Explore skill instructions resolve from the skills-era location on 1.5+/1.6 projects, with pre-1.5 fallbacks intact.
- This repo's tracked skill surface matches what a 1.6.0 CLI generates.

**Non-Goals:**
- Surfacing `/opsx:update` (the new agent workflow) as a plugin feature — it is an agent skill with no CLI subcommand, and the plugin's Update action already covers `openspec update` itself.
- Any handling of nested-tasks (`apply.tracks`) aggregation — the plugin reads a change's single `tasks.md`, which 1.6 leaves identical.

## Decisions

1. **Prepend, don't replace.** `.claude/skills/openspec-explore/SKILL.md` becomes the first lookup path; the legacy command paths stay as fallbacks because 1.3/1.4 projects remain supported and still have them. The existing YAML-frontmatter stripping handles the 1.6 stamps unchanged.
2. **Regenerate skills with the real CLI, not by hand.** The tracked `openspec-*` dirs are CLI output; this change commits exactly what `openspec update` at 1.6.0 writes (verified: six dirs, stamped frontmatter, custom-named skills untouched).

## Risks / Trade-offs

- [A project has both skills-era and legacy files with divergent content] → skills-era wins, matching what the CLI itself maintains as current.
