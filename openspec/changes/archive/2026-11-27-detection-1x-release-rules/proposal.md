# Proposal

## Why

The plugin's 1.x tool detection applies 1.17's rules and tool table to every 1.x release. Upstream's `getAvailableTools` and its tool registry changed within 1.x, so on older 1.x CLIs the plugin reports tools the CLI doesn't, and misses ones it does. Captured runs show 12 such cases, on 10 new layouts and 5 existing ones captured on more releases. Among them:
- a bare `.codex/` is Codex to 1.7.0 and nothing to 1.8.0;
- a `.agents/workflows` folder is Antigravity only from 1.11.0;
- `.amp/` and `.gsd/` count only from 1.14.0;
- AiderDesk, a 2.1 tool, is reported even on 1.17.0.

Tracked in the linked tracker entry.

## What Changes

- **1.x detection follows the detected 1.x release.** The rules that arrived within 1.x apply only from their release:
  - reading a tool's home-directory skills;
  - attributing a shared skills root by its marker, then its skill syntax (1.8.0);
  - a tool's own detection path counting beside a shared root it doesn't own (1.11.0).
- **The tool table is the release's own.** A tool counts only from the release that added it, and before 2.1 for a 2.1 tool. A tool whose folders changed within 1.x is looked for where that release kept it:
  - Kimi: `.kimi`, then `.kimi-code` (1.7.0);
  - Devin: `.windsurf`, then `.devin` (1.7.0);
  - Codex: `.codex`, then `.agents` (1.8.0);
  - `agents`: no folder, then `.agents` (1.8.0);
  - Antigravity: `.agent`, then `.agents` (1.11.0).
- **The set-up verdict follows the release too.** It counts only the release's tools, in their shapes then. Commands count from 1.7.0, and a tool's commands in an older folder from 1.17.0. On 1.7.0 a generic skill under `.agents/skills` set up 15 tools; the CLI counts none.
- **Unchanged:** when no CLI release is known, detection is exactly today's: 1.17's shapes and rules, with no release filter. On a detected 1.17.x it gains only the release filter, so a 2.1 tool such as AiderDesk is no longer reported. 2.x detection is unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: AI tool detection follows each 1.x release's rules and tool table, not only 1.17's. A tool's earlier folder counts only on the releases that used it.

## Impact

- **Code:** `AiToolDetector` (the 1.x path and the shared-root owner take the release), `AiToolRegistry` (each tool's shape as of a 1.x release), `AiToolDetectionService` (passes the detected CLI's version to detection and the set-up verdict).
- **Fixtures:** 10 new detection layouts, plus 5 existing ones, captured across 1.3.1, 1.4.0, 1.6.0, 1.7.0, 1.8.0, 1.10.0, 1.11.0, 1.13.2, 1.14.0 and 1.17.0. The registry check now covers every captured 1.x registry, from 1.3.0.
- **Tests:** `AiToolDetectionContractTest` passes on every captured release, and the known-gap entry for this change is removed.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unaffected.
