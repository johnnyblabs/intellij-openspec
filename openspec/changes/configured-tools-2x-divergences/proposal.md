# Proposal

## Why

Manage AI Tools' "set up" status differs from what the OpenSpec CLI itself counts as configured (`getConfiguredTools`). The CLI's own captured verdicts show it:

| Case | The CLI | The plugin | Where |
|---|---|---|---|
| Only `.agents/skills/.openspec-target` names Codex, with no skill file | Codex set up | Codex not set up | 1.17.0, 2.0.0, 2.1.0, 2.1.1 |
| Kimi's skills only in the 1.6-era `.kimi/skills/` | Kimi not set up | Kimi set up | the same |
| Devin's skills only in the 1.6-era `.windsurf/skills/` | Devin not set up | Devin set up | the same |
| `.openspec-target` names GSD in a shared `.agents/skills/` | GSD set up | the universal `.agents` tool set up | 1.17.0 |

The contract test records the 2.x cases as known differences and doesn't check 1.x at all. Nothing captured shows the CLI's verdict below 1.17 either, so the release where each rule starts is unproven. The plugin follows the client rather than its own verdict, so it should match on every CLI it supports. Most users still run 1.17 until the CLI bump, so 1.x matters as much as 2.x.

## What Changes

- **On CLI 2.x, a tool the list names counts as set up.** The plugin counts a tool as set up when its existing port of the CLI's `isListedToolPresent` says so: the tool shares `.agents/skills`, and the list names it (by the CLI's own reading, inference and aliases included), either without an `own-files` mark or with files or links of its own.
- **On CLI 1.8 and later, the tool that owns a shared skills folder counts, and only that tool.** Where several tools write to one skills folder (`.agents` for the universal tool, Codex, Amp, GSD and others), the CLI decides which of them owns it:
  1. the tool the one-line `.openspec-target` names;
  2. else the tool inferred from the skills' invocation syntax, with a legacy-Codex special case;
  3. else the universal tool when the folder holds current skills;
  4. else a tool with older skills of its own.
  
  That tool counts as set up, even with no skill file yet when the list names it; the others don't count from that folder. The plugin follows the same order.
- **A 1.6-era folder no longer counts on CLI 1.7 and later.** From 1.7 on, `.kimi` and `.windsurf` are only detection paths: they show Kimi Code and Devin Desktop are present, not that OpenSpec set them up, since those tools moved to `.kimi-code` and `.devin`. The plugin shows such a tool as detected, so Manage AI Tools offers to set it up again. Folders the CLI lists as a tool's older skills folders (`.codex` for Codex, `.agent` for Antigravity) keep counting. On 1.6 and earlier, where `.kimi` and `.windsurf` were those tools' skills folders, they keep counting too.
- **A tool can be set up without being detected.** The CLI counts the folder's owner as configured even when the tool's own detection path is absent (GSD without `.gsd`); Manage AI Tools shows it as set up.
- **The contract test covers 1.6.0, 1.7.0, 1.17.0, 2.0.0, 2.1.0 and 2.1.1, and records no known differences.** New captures prove each boundary from the CLI itself: the 1.6-era folders on 1.6.0 and 1.7.0, and a tool the 2.x list marks `own-files` whose links are gone.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: a new requirement for how a tool's set-up status follows the CLI's tool list, shared-folder ownership and folders.

## Impact

- **Code:**
  - `AiToolDetectionService`: the set-up verdict, and tools set up without being detected;
  - `Detector2x`: its `isListedToolPresent` port and owner inference, exposed to the verdict;
  - `AiToolDetector`: its existing port of `reconcileSharedSkillTargets`, exposed to the verdict.
- **Tests:** `ConfiguredToolsContractTest` (1.6.0, 1.7.0, 1.17.0 and 2.0.0 added, no known differences), plus focused tests for each rule against the captured layouts.
- **Fixtures:** the capture script gains a case filter and a folder-removal step; new captures for 1.6.0, 1.7.0 and the `own-files` case on 2.x.
- **Docs:** CHANGELOG; the Manage AI Tools section of `docs/feature-reference.md` where it describes set-up status.
- **Platform:** no new platform APIs.
