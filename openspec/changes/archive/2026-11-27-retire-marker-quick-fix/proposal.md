# Proposal

## Why

Since CLI 1.8.0, when a change removes a capability's last requirement without an honorable `retire_capabilities: true` in its `.openspec.yaml`, `openspec archive` refuses. Its fix tells the author to add the marker and rerun. No OpenSpec command writes the marker: only the author can authorize deleting a spec, so the CLI prints the fix for them to apply. The plugin now shows that refusal (Sync Specs, archive), but the user still has to find the change's metadata file, add the line by hand, and start again. Tracked in the linked tracker entry.

## What Changes

- **A confirmed action applies the CLI's printed fix.** Where a refusal's fix names the marker, the plugin offers "Add retire_capabilities: true…". After a confirmation naming every spec the marker would make deletable (the CLI names only the first it refuses; the plugin lists them all), it writes `retire_capabilities: true` into the change's `.openspec.yaml`. It replaces a non-boolean value and keeps every other key, comment and line. The edit can be undone.
- **It is offered only where adding the marker is the fix.** That means the CLI's (or the plugin's) refusal says "This change removes the last requirement … add `retire_capabilities: true`", and any "cannot be honored" reason is about the marker's own value, and the metadata with the marker added would be honored (the plugin checks this, because its own refusal sometimes leaves the reason out). It is not offered for content the deletion would take, an invalid metadata file, a missing `schema:`, a malformed `created:`, an unknown or invalid schema, or below CLI 1.8, since the marker fixes none of those.
- **Nothing is deleted by the action.** After writing, it opens the Sync preview again, where the retirement shows as `<id> (retire)` with its banner. Deleting stays the job of Apply, or of a later archive.
- **Surfaces:**
  - A link in the Sync preview's refusal panel.
  - An action on the "archive failed" notification, which opens the Sync preview and does not re-run the archive.
  - Bulk Archive rows refused for the marker only gain a hint to use Sync Specs on that change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `spec-sync`: an action that adds the retirement marker when a refusal asks for it.

## Impact

- **Code:** a new `RetireMarkerFix` (the gate, the confirmation list, the metadata edit), `ArchiveDispatch` (the offer, decided off the UI thread), `SyncPreviewDialog` (link), `OpenSpecNotifier` and the three archive entry points (notification action), `BulkArchiveDialog` (hint).
- **Tests:**
  - The gate against every captured `retire/` refusal on 1.8.0, 1.11.0, 1.16.1 and 2.2.0.
  - After the edit, the plugin's merge retires, as the CLI does on the equivalent captured case.
  - The edit keeps the file's other content.
- **Docs:** feature reference (Sync Specs, Archive), CHANGELOG.
- **Platform:** `WriteCommandAction` on the metadata file's `Document`, `ActionLink`, `NotificationAction`, all available on 2024.2. IntelliJ 2024.2+ support is unaffected.
