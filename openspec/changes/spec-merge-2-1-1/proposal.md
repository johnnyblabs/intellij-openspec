# Proposal

## Why

Sync Specs, and the plugin's archive when no CLI handles it, promise a new main spec byte-identical to what `openspec archive` writes. CLI 2.1.1 changed how it writes and reads a new spec's Purpose, so on 2.1.1 the plugin's new specs now differ from the CLI's. Tracked in the linked tracker entry, part of CLI 2.x support.

## What Changes

- **A new main spec's Purpose layout follows the release.** From CLI 2.1.1 the CLI writes `## Purpose`, a blank line, then the body. Earlier releases (1.x to 2.1.0) write the body on the line after the heading. The plugin writes the layout of the release it follows: the detected CLI, or its target release with none. Existing main specs are untouched either way.
- **Reading the delta's Purpose follows the release.** From 2.1.1 the CLI drops a leading byte-order mark and reads past a delta's front matter, so a comment opener or fence inside the front matter can't hide the delta's `## Purpose`. Before 2.1.1 neither happens. The plugin matches each.
- **Contract-tested against real archive output.** New archive captures from CLI 2.1.0 and 2.1.1 (new capability with and without a Purpose, a short Purpose, a byte-order mark, and front matter around the Purpose). The merge is held to them, and the existing 1.14.3 captures keep holding.
- **Out of scope: retiring a capability.** The CLI deletes a main spec when a change marked `retire_capabilities` removes its last requirement, auditing what would be lost (an audit 2.1.1 also changed). The plugin has never ported retirement, on any release, so it gets its own tracker entry rather than riding on this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `spec-sync`: a new requirement that a new main spec, and the Purpose read from the delta, follow the release the plugin follows.

## Impact

- **Code:** `SpecMerge` (the new-spec skeleton and the delta Purpose reader take the release); `SpecSyncService` and the plugin's archive path pass the followed release (`ValidationRelease.followed`).
- **Tests:** `ArchiveMergeContractTest` replays 2.1.0 and 2.1.1 captures as well as 1.14.3's.
- **Fixtures:** `scripts/capture-archive-fixtures.py` runs a scratch-installed release (never a pinned `npx`) and gains the byte-order-mark and front-matter cases.
- **Docs:** CHANGELOG.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unaffected.
