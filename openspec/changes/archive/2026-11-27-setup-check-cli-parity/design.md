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

### D3. A machine-wide install (CLI 2.1+)

The plugin counted a detected tool as set up when `SkillLocator` found its skills anywhere else, such as a home-directory folder or a link. No release does this: `getConfiguredTools` reads only the project. Upstream models the machine separately: `readGlobalSkills`, `projectHasOwnSkills` and `update`'s `hasGlobalSkills` (`dist/core/global-skills.js`, `update.js`).

The captures (`machine-wide/`) show it on 2.1.0, 2.1.1 and 2.2.0:
- With skills only for the machine, the tool is not set up.
- `init --tools claude` (default scope) refreshes the machine-wide copy ("N skills in ~/.agents/skills/ for every project on this machine"), and the tool still isn't set up.
- `update` says only "The N skills for this machine are up to date".
- For a tool without global support (`supportsGlobalSkills`: `eve`, `promptscript`), `init` installs into the project, and the tool is set up.

Two further audit findings, both verified on real 2.2.0 runs, shaped the rule:
- `init` doesn't only refresh. `installGlobalSkills` adds the named tool's agent, so a tool whose own machine folder is empty (Claude, beside a filled `~/.agents/skills`) gets one. Hiding Configure would remove the only way to do that.
- Upstream picks the project whenever the project has any set-up tool, commands and tool lists included (`project-commands-only`), or a saved scope says project (`saved-scope-project`).

So:
- **Removed:** the fallback.
- **Flagged:** `ToolInfo.installsForMachine` marks a detected tool for which `init` installs into this machine. That is `resolveSkillScope` for one tool on CLI 2.1+:
  - the project has no skills of its own (no tool set up in it, which is `projectHasOwnSkills`);
  - no saved scope says `project` (`OPENSPEC_SKILLS_SCOPE`, else `skillsScope` in the CLI's global `config.json`, read by `SavedSkillsScope`);
  - the tool supports global skills.
- **Proof:** it's contract-tested against `machine-wide/` as exactly "the CLI's own `init --tools <tool>` left the tool not set up for the project".
- **Manage AI Tools:**
  - those tools sit under "DETECTED — skills for this machine";
  - the caption is "Not set up for this project. Configure installs or refreshes these tools' skills for every project on this machine, as openspec init does here. To give one skills of its own, use Install into this project above.";
  - the row note is "skills for this machine";
  - Configure stays, with a tooltip saying what it does. It passes no scope (`initKeepingScope`), so the CLI decides as it always does.

### D4. Adapter-backed tools' older folders (1.11.0)

From 1.11.0, `getConfiguredTools` counts an adapter-backed tool's older-folder skills whoever owns the shared folder (`resolveCommandSurfaceCapability(t) === 'adapter-backed' && hasLegacySkills`). Only Antigravity qualifies on every release from 1.11.0 to 2.2.0 (`adapterBackedWithLegacy` in `<v>/skill-names.json`). The fallback had hidden this case (`antigravity-legacy-skill-beside-agents-owner` on 1.11.0).

### Out of scope

The skills-scope check, `ProjectSkillsScope`, behind the scope line, has three differences from upstream. It reads only `~/.agents/skills`, while upstream also reads each tool's own home folder; `machineFolders` in `machine-wide/global-support.json` records upstream's folder per tool. It treats the home folder as a project. And it decides "in project" from skill files rather than from set-up tools. The linked follow-up fixes them.

## Risks

- **The newest list with no CLI** keeps today's behavior for the router, and stops counting folders with no `SKILL.md`. No release ever counted those, and the plugin creates none.
