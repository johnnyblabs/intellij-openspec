# Design

## Context

Upstream decides what an OpenSpec skill is the same way in every release. Its `getConfiguredTools` and `getAvailableTools` (`dist/core/shared/tool-detection.js`, `dist/core/available-tools.js`) check `path.join(skillsDir, name, 'SKILL.md')` for each name in its list:
- `SKILL_NAMES` in `tool-detection.js` on 1.3–1.7;
- `OPENSPEC_SKILL_NAMES` in `config.js` from 1.8.

That list is 11 names on 1.3–1.5. 1.6 adds `openspec-update-change`, making 12. 2.2 adds the `openspec` router, making 13. The plugin keeps one 13-name `AiToolRegistry.SKILL_NAMES`. Its set-up check `hasOpenSpecSkills` accepts any `skills/openspec-*` folder, whether or not a `SKILL.md` is in it.

## Decisions

### D1. The release's skill names

`AiToolRegistry.skillNames(@Nullable String release)` returns the list:
- 11 names below 1.6.0;
- 12 below 2.2.0;
- 13 from 2.2.0, and with no release (the newest list).

The existing `SKILL_NAMES` stays as the newest list for callers that look up skills for invocation rather than verdicts.

### D2. Where it applies

- **The set-up verdict:** `hasOpenSpecSkills(dir, names)` is true when `dir/skills/<name>/SKILL.md` is a file for some name. The names are the detected CLI's (`cliVersion()`), on either line.
- **1.x detection:** the home-directory check for global tools, the skill-syntax reading of an unmarked shared folder, and the current and legacy skill checks in reconciliation take the 1.x release's names (`detect1x`'s release).
- **2.x detection is unchanged.** Its captured verdicts, including the router alone in `.agents/skills` on 2.1.1 and 2.2.0 (`router-skill-shared-only`), already match.

### D3. The located-skill fallback (open)

A detected tool also counts as set up when `SkillLocator` finds one of its skills somewhere else: a home-directory folder or link. No release's `getConfiguredTools` does this. On 1.17.0, 2.1.1 and 2.2.0, a `.claude` folder plus home-directory skills (`claude-folder-with-home-skills`) is not set up. The plugin's contract test can't see the fallback, because it runs with no home snapshot.

Whether to drop it depends on what upstream 2.1, which installs machine-wide by default, treats as a tool being set up. The openspec-guru ruling decides before implementation: drop it to match, or keep it as a stated, deliberate difference.

## Risks

- **The newest list with no CLI** keeps today's behavior for the router, and stops counting folders with no `SKILL.md`. No release ever counted those, and the plugin creates none.
