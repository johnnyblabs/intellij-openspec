## Why

The plugin includes Forgejo and Plane tracker integration (issue creation, status updates, archive sync) that duplicates what the external AI-assisted workflow already handles. The tracker settings UI was never built, so the feature is unreachable from the IDE. Removing it simplifies the plugin and eliminates a source of potential double-updates when both the plugin and CLI workflows manage the same tracker issues.

## What Changes

- **BREAKING**: Remove all tracker services (`ForgejoService`, `PlaneService`, `IssueLifecycleService`, `ArchiveSyncService`, `TrackerCredentialStore`, `TrackingMetadataWriter`)
- **BREAKING**: Remove tracker fields from `OpenSpecSettings` (Forgejo/Plane URL, credentials, enabled flags)
- **BREAKING**: Remove `TrackingMetadata` model from `ChangeMetadata`
- Remove tracker lifecycle calls from Propose, Apply, and Archive actions
- Remove post-archive sync UI and retry button from `WorkflowActionPanel`
- Remove tracker notification group and service registrations from `plugin.xml`
- Delete tracker test files

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `issue-tracking`: Removing this capability entirely — all requirements are being deleted

## Impact

- **Code**: 7 source files + 2 test files deleted; 6 files edited to remove tracking references
- **Settings**: 8 persisted state fields removed from `OpenSpecSettings` — existing settings XML will have orphaned keys that IntelliJ ignores silently
- **YAML model**: `tracking` block in `.openspec.yaml` files will be ignored by the plugin (non-breaking for file parsing)
- **User-facing**: No visible change — tracker features were never exposed in the settings UI
- **External workflow**: No change — CLI-based tracker updates (Claude Code skills, Copilot prompts) continue to work independently
