## Why

The Plane workspace at `http://plane.geek` has two projects when only one was intended — the setup script creates "OpenSpec Plugin" (OSP) but a duplicate likely exists from manual creation. Beyond the duplicate, Plane's feature set is significantly underutilized: we only use cycles (as releases), labels, and basic work items. Features like custom workflow states, modules, pages, views, native priority, and estimates are untouched. This means we're paying the overhead of a second project management tool without leveraging what makes Plane valuable — its visual planning, sprint-like workflows, and documentation capabilities.

Now is the right time because we just shipped automated issue lifecycle integration, and the `PlaneService` and setup scripts need to work against a clean, well-configured workspace.

## What Changes

- **Audit & consolidate projects**: Identify and remove the duplicate project, ensure only one "OpenSpec Plugin" (OSP) exists
- **Configure custom workflow states**: Replace the default states with a meaningful workflow (Backlog → Todo → In Progress → In Review → Done → Cancelled)
- **Create modules**: Organize work items by feature area (Core, UI, AI Integration, Validation, Documentation, Infrastructure)
- **Create pages**: Set up project documentation pages (Architecture Overview, Release Notes, Development Guide)
- **Create saved views**: Add filtered views (Current Sprint, High Priority, By Component, Blocked Items)
- **Migrate priority labels to native priority**: Use Plane's built-in priority field (Urgent, High, Medium, Low, None) instead of `priority:high/medium/low` labels
- **Add estimates**: Enable story point estimation on work items for velocity tracking
- **Update setup script**: Extend `setup-plane.sh` and `plane-api.sh` to configure all new features idempotently
- **Update sync script**: Extend `sync-status.sh` to use proper state transitions instead of just "Done"
- **Update PlaneService**: Teach the plugin's `PlaneService` to use proper states when creating/updating work items

## Capabilities

### New Capabilities
_(none — this is infrastructure/tooling, no new plugin spec capabilities)_

### Modified Capabilities
- `issue-tracking`: PlaneService state transitions should use the configured workflow states (In Progress, Done) rather than searching by name at runtime

## Impact

- Scripts: `scripts/setup-plane.sh`, `scripts/lib/plane-api.sh`, `scripts/sync-status.sh`
- Data: `scripts/docs/data/plane-workitems.json` (add priority, module, estimates fields)
- Plugin: `src/main/java/com/johnnyb/openspec/tracking/PlaneService.java` (minor — use configured state IDs)
- Plane workspace: project consolidation, new states/modules/pages/views
- No new dependencies
