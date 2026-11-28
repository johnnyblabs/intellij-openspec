# Proposal

## Why

The plugin re-implements parts of the OpenSpec client: tool detection (`getAvailableTools`), set-up status (`getConfiguredTools`), the skills scope (`readGlobalSkills`, `projectHasOwnSkills`), the archive merge and its retirement check, and more. Every check so far has been scoped to the diff in front of it, so divergences in code next to a change surface one at a time, after they ship.

In three consecutive changes, each audit found older divergences in neighboring code:
- 1.x detection used 1.17's rules on every release;
- set-up status was looser than every release;
- the skills scope reads fewer folders than upstream.

Nothing records which upstream function each port mirrors, the releases where its behavior changes, or the captures that prove each side of each change. Nothing flags plugin code with no upstream counterpart. Tracked in the linked tracker entry.

## What Changes

- **A parity inventory, `docs/cli-parity.md`** (Maintenance: Living, vendor-neutral). It has one row per ported upstream function. Each row gives:
  - the upstream source file;
  - the releases where its behavior changes;
  - for each change, the captures on both sides;
  - the contract test that replays them;
  - any recorded gap or deliberate difference.

  It is linked from the feature-level matrix in `docs/openspec-support.md`, which stays the parity north-star for features.
- **An inventory test**, so the doc can't drift from the tree. Every contract test the inventory names must exist. Every fixture folder it names must exist. Every `@CliFixtureSuite` class must appear in a row or be listed as not a port.
- **A standing full-surface parity sweep** by openspec-guru, at every CLI bump and before every release. It diffs each inventoried function across all cached releases, and reports any behavior change the inventory doesn't record, and any port with no row. It runs beside the existing workflow↔UI parity audit and Direct API provider gate (`CLAUDE.md`, release prep).
- **Two rules in the agents' definitions:**
  - openspec-guru and test-engineer require every ported rule to cite `dist/<file>:<line>` and to have a capture on both sides of each release change.
  - Plugin code with no upstream counterpart is flagged, then removed or recorded as a named, deliberate difference.
- **A boundary-coverage check in test-engineer's audit mode.** For each version check a diff adds, the captures on each side are named.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This changes maintainer tooling and docs, not plugin behavior (`skip_specs: true`).

## Impact

- **Files:**
  - `docs/cli-parity.md` (new), `docs/openspec-support.md` and `docs/README.md` (links);
  - a new inventory test;
  - `CLAUDE.md` (agent routing, release prep);
  - `.claude/agents/openspec-guru.md`, `.claude/agents/test-engineer.md`;
  - the `release-prep` skill, which is clone-local.
- **Expected:** the first sweep will likely find more divergences. Each becomes a tracked entry, not part of this change.
- **Not affected:** plugin code, specs, and the IntelliJ 2024.2+ support range.
