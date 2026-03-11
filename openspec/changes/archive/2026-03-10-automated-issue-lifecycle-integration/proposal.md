## Why

After completing a change (commit, archive, push), we manually create or update issues in Forgejo and work items in Plane. This is tedious, error-prone, and often forgotten. Automating issue lifecycle across both systems — from proposal through archive — eliminates this overhead, ensures traceability between OpenSpec changes and project management artifacts, and keeps both systems in sync without extra effort.

## What Changes

- Add Forgejo REST API client service with token-based authentication
- Add Plane REST API client service with API key authentication
- Store Forgejo/Plane credentials via IntelliJ PasswordSafe (same pattern as AI provider keys)
- Add issue tracking configuration to settings (server URLs, project identifiers, enable/disable per system)
- Hook into change lifecycle events: Propose creates issues, Apply updates status, Archive closes/resolves
- Store issue/work-item IDs in `.openspec.yaml` metadata for bidirectional linking
- Add issue status indicators to the tool window tree (linked/unlinked state)

## Capabilities

### New Capabilities
- `issue-tracking`: Core integration services for Forgejo and Plane — API clients, credential storage, lifecycle event hooks, and `.openspec.yaml` metadata linking

### Modified Capabilities
- `actions`: Propose, Apply, and Archive actions trigger issue creation/status updates in configured trackers
- `settings-panel-sections`: Add "Issue Tracking" settings group for Forgejo/Plane server URLs, credentials, and enable/disable toggles

## Impact

- New services: `ForgejoService`, `PlaneService`, `IssueLifecycleService`
- New settings fields in `OpenSpecSettings` for tracker configuration
- Modified actions: `OpenSpecProposeAction`, `OpenSpecApplyAction`, `OpenSpecArchiveAction`
- Modified `.openspec.yaml` schema: adds optional `tracking` metadata block
- New dependencies: none (uses existing Java 11+ HttpClient and Gson)
- Plugin.xml: register new services
