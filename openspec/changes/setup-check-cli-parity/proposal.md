# Proposal

## Why

The set-up requirement says a tool is set up exactly when the CLI's `getConfiguredTools` says so. Captured runs show the plugin's check is looser than every release in how it recognizes an OpenSpec skill:
- it counts any `skills/openspec-*` folder, even one with no `SKILL.md`, or a name no release uses (`openspec-custom`);
- it applies every release's names to all of them. `openspec-update-change` counts on 1.5.0, which doesn't have it, and the 2.2 router skill `openspec` counts on 1.17.0 and 2.1.1.

In each of these cases the CLI says not set up and the plugin says set up. The same name list makes 1.x detection report MiniMax Code from a router-only home folder on 1.8.0 and 1.17.0, which detect nothing. Tracked in the linked tracker entry.

## What Changes

- **An OpenSpec skill is the release's own.** A skill counts when `<skills folder>/<name>/SKILL.md` exists for a name in the detected release's list:
  - 11 names on 1.3–1.5;
  - 12 on 1.6 through 2.1.x, which add `openspec-update-change`;
  - 13 from 2.2, which adds the `openspec` router.

  With no CLI detected, the newest list applies. A folder without `SKILL.md`, or an unlisted name, never counts.
- **Set-up status and 1.x detection use that rule.** This covers the set-up verdict's skill checks, 1.x detection's home-directory tools, and its shared-folder ownership reading.
- **From 1.11.0, an adapter-backed tool's older-folder skills count whoever owns the shared folder**, as upstream's `getConfiguredTools` counts them. Only Antigravity qualifies, captured per release.
- **A machine-wide install is the machine's, not the project's** (CLI 2.1+).
  - A tool whose skills exist only for this machine is detected, not set up, as `getConfiguredTools` says. The plugin's located-skill fallback, which counted it as set up, is removed.
  - When `openspec init` would install a detected tool for this machine rather than the project, Manage AI Tools lists it under "DETECTED — skills for this machine", with a caption saying so. That is upstream's `resolveSkillScope`: the project has no set-up tool, no saved scope says project, and the tool supports global skills.
  - Configure stays. Its tooltip says it installs or refreshes the tool's skills for every project on this machine, exactly what the CLI does there.
  - "Install into this project" remains the way to give it skills of its own.
- **Unchanged:** 2.x detection (its captured verdicts already match), commands, and the tool lists.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: set-up status and 1.x detection count only the release's own OpenSpec skills, each as `<name>/SKILL.md`.

## Impact

- **Code:** `AiToolRegistry` (the release's skill names), `AiToolDetectionService` (the set-up verdict's skill check), `AiToolDetector` (1.x name uses).
- **Fixtures:** per-release skill names and adapter-backed tools (`<v>/skill-names.json`, 1.3.0-2.2.0); machine-wide runs (`<v>/machine-wide/`, 2.1.0-2.2.0: the CLI's verdicts plus `init` and `update` output); 8 new detection captures across 1.5.0, 1.6.0, 1.7.0, 1.8.0, 1.17.0, 2.1.1 and 2.2.0, each with the CLI's detected and set-up verdicts.
- **Tests:** `ConfiguredToolsContractTest` and `AiToolDetectionContractTest` pass on every captured release.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unaffected.
- **UI:** Manage AI Tools (`ManageAiToolsDialog`) gains a group, a caption and a row note for those tools, and Configure's tooltip says what it does; this needs the maintainer's uiSmoke run.
- **Follow-up:** the skills-scope check itself (`ProjectSkillsScope`) follows upstream's `readGlobalSkills` and `projectHasOwnSkills`, in a separate change (the linked follow-up entry).
