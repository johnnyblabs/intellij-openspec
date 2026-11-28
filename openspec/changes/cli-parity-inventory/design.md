# Design

## Context

There are three kinds of evidence today:
- **Captured fixtures** (`src/test/resources/fixtures/cli/`, indexed by its README).
- **Contract tests** (`@CliFixtureSuite`, about 25 classes).
- **A feature matrix** (`docs/openspec-support.md`), whose statuses include "Divergent" and "Plugin-original".

What's missing is the layer between them: which upstream function a port mirrors, and over which releases.

## Decisions

### D1. One row per ported function

| Column | Content |
|---|---|
| Upstream | the function and its file (`dist/core/available-tools.js` `getAvailableTools`) |
| Plugin | the class and method that port it |
| Release changes | each release where behavior changes, one line each (`1.8.0`: reconciliation) |
| Captures | for each change, the fixture cases on both sides (`1.7.0`/`1.8.0` `builtin-add-codex-list-on-empty`) |
| Contract test | the class that replays them |
| Gaps / differences | `KnownGaps` keys and named deliberate differences, each with its tracker entry |

The first rows come from the changes already merged:
- `getAvailableTools`, `getConfiguredTools`, `SKILL_NAMES` and `resolveSkillScope`;
- the archive merge, `contentTheMergeCannotName` and `readBooleanMarker`;
- `ChangeMetadataSchema`, the tool registry (`AI_TOOLS`) and the skills layout.

The first sweep completes the table.

### D2. The inventory test

A plain test reads `docs/cli-parity.md` and checks three things:
- every contract-test class named exists;
- every fixture path named exists;
- every `@CliFixtureSuite` class is named in a row or in a short "not a port" list (for example, guard tests).

It doesn't parse upstream. The sweep (D3) does that, because a test can't judge whether a row is complete.

### D3. The sweep

openspec-guru runs it, with the preloaded `upstream-evidence` skill, at every CLI bump and before every release. For each row, it diffs the upstream file across every cached release since the row's last checked release, and reports:
- a behavior change with no "release changes" line;
- a change with no captures on one side;
- a port it finds with no row.

Each finding becomes a tracked entry. The sweep records the release it checked up to in the row.

### D4. Agent rules

- **openspec-guru:** a ported rule needs a dist citation and captures on both sides of each release change. Any plugin rule with no upstream counterpart is flagged.
- **test-engineer** (audit mode): for each version check in the diff, name the captures before and after it. Missing ones are findings.

`CLAUDE.md` gets the sweep as a trigger point beside the workflow↔UI parity audit, and release prep lists it with the other BLOCKER gates.

## Risks

- **The inventory goes stale between sweeps.** The inventory test keeps names honest, and the sweep keeps content honest. A change that adds a port adds its row in the same PR: the doc-fidelity rule.
