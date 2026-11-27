# Proposal

## Why

Since CLI 1.8.0, `openspec archive` retires a capability. When a change whose `.openspec.yaml` sets `retire_capabilities: true` removes a capability's last requirement, the CLI deletes its main spec. It first checks the spec for content the merge can't account for, and refuses when there is any. Without the marker, it refuses and says how to retire. The plugin's own merge, used by Sync Specs on every release and by archive without a CLI (or below 1.5), never retires: it writes a spec with an empty `## Requirements` section, which no release would write. Tracked in the linked tracker entry.

## What Changes

- **The plugin's merge retires a capability as the followed release does.** With the marker, a change removing the last requirement deletes the main spec, then any folders left empty up to `specs/`, when the spec holds nothing the merge can't account for. That check follows the release: as 1.8–1.12, as 1.13–2.1.0, or as 2.1.1 and later.
- **It refuses as that release refuses**, in its wording: content the merge can't account for, naming the first lines; or no marker, with the hint that release gives. Below 1.8 it refuses without a hint, as those releases do.
- **Sync Specs shows a retirement**: the capability's tab reads `<id> (retire)`, a banner says which spec is deleted, and Apply deletes it. A refusal blocks Apply, as other refusals do.
- **Archive says what it deletes.** The archive prompt names the specs a retirement deletes; the result says which capabilities were retired and how to restore a committed spec. When the CLI archives, the plugin reads its "Retiring …" lines to say the same.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `spec-sync`: capability retirement in Sync Specs and the plugin's archive, and reporting the CLI's.

## Impact

- **Code:** `SpecMerge` (the gate, the three release checks, the refusals), `SpecSyncResult` (a retirement), `SpecSyncService.applySync` (delete and prune), `SyncPreviewDialog`, `ArchiveSyncPrompt`, `ArchiveDispatch` (summary; the CLI's "Retiring" lines).
- **Fixtures:** real archive runs on 1.7.0, 1.8.0, 1.11.0, 1.16.1 and 2.2.0 (a new capture script), contract-tested.
- **Docs:** feature reference (Sync Specs, Archive), CHANGELOG.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unaffected.
- **Not here:** a quick fix that adds `retire_capabilities: true` (a separate change).
