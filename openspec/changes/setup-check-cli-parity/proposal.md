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
- **Unchanged:** 2.x detection (its captured verdicts already match), commands, and the tool lists.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: set-up status and 1.x detection count only the release's own OpenSpec skills, each as `<name>/SKILL.md`.

## Impact

- **Code:** `AiToolRegistry` (the release's skill names), `AiToolDetectionService` (the set-up verdict's skill check), `AiToolDetector` (1.x name uses).
- **Fixtures:** 8 new detection captures across 1.5.0, 1.6.0, 1.7.0, 1.8.0, 1.17.0, 2.1.1 and 2.2.0, each with the CLI's detected and set-up verdicts.
- **Tests:** `ConfiguredToolsContractTest` and `AiToolDetectionContractTest` pass on every captured release.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unaffected.
- **Open question (design D3):** the located-skill fallback, which counts a detected tool's skills found elsewhere, such as the home directory. That depends on what upstream 2.1 treats as set up for a machine-wide install, and is ruled on before implementation.
