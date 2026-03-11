## 1. Audit and consolidate projects

- [x] 1.1 List all projects in the Plane workspace via API (`GET /api/v1/workspaces/openspec/projects/`) and identify the duplicate
- [x] 1.2 Document which project is the correct one (has work items, cycles, labels) vs the duplicate
- [x] 1.3 Delete the duplicate project via Plane UI (manual step — confirm with user first)

## 2. Configure workflow states

- [x] 2.1 Add `plane_create_state` and `plane_get_states` functions to `scripts/lib/plane-api.sh`
- [x] 2.2 Add state configuration to `scripts/setup-plane.sh`: create Backlog (backlog), Todo (unstarted), In Progress (started), In Review (started), Done (completed), Cancelled (cancelled)
- [x] 2.3 Add a `scripts/docs/data/plane-states.json` data file defining the 6 states with names, groups, and colors
- [x] 2.4 Map existing work items to appropriate states (items marked done → Done, others → Backlog)

## 3. Create modules

- [x] 3.1 Add `plane_create_module` and `plane_get_module_id` functions to `scripts/lib/plane-api.sh`
- [x] 3.2 Add a `scripts/docs/data/plane-modules.json` data file defining 6 modules: Core, UI, AI Integration, Validation, Documentation, Infrastructure
- [x] 3.3 Add module creation to `scripts/setup-plane.sh`
- [x] 3.4 Add module assignments to work items in `scripts/docs/data/plane-workitems.json` and wire up in setup script

## 4. Create pages

- [x] 4.1 Add `plane_create_page` function to `scripts/lib/plane-api.sh`
- [x] 4.2 Create page content files: `scripts/docs/data/plane-pages/architecture.md`, `release-notes.md`, `dev-guide.md`
- [x] 4.3 Add page creation to `scripts/setup-plane.sh`

## 5. Create views

- [x] 5.1 Add `plane_create_view` function to `scripts/lib/plane-api.sh`
- [x] 5.2 Add a `scripts/docs/data/plane-views.json` data file defining 4 views with filter criteria
- [x] 5.3 Add view creation to `scripts/setup-plane.sh`

## 6. Migrate priority labels to native priority

- [x] 6.1 Add a migration script or section in `setup-plane.sh` that reads each work item's labels, maps `priority:*` to native priority field, and patches the work item
- [x] 6.2 Remove `priority:high`, `priority:medium`, `priority:low` from `scripts/docs/data/labels.json`
- [x] 6.3 Update `scripts/docs/data/plane-workitems.json` to use `priority` field instead of priority labels

## 7. Enable estimates

- [x] 7.1 Add estimate configuration to `scripts/setup-plane.sh` (enable estimates with Fibonacci scale: 1, 2, 3, 5, 8, 13)
- [x] 7.2 Verify via API that estimate points are available on the project

## 8. Update sync script

- [x] 8.1 Update `scripts/sync-status.sh` to use proper state transitions (In Progress, Done) instead of searching by name
- [x] 8.2 Add state lookup caching to avoid repeated API calls during sync

## 9. Update PlaneService

- [x] 9.1 Update `PlaneService.updateWorkItemState()` to use state name constants ("Backlog", "In Progress", "Done") matching the configured workflow
- [x] 9.2 Update `IssueLifecycleService.onPropose()` to set initial state to "Backlog" when creating work items

## 10. Verify

- [x] 10.1 Run `./gradlew clean build test` — all green
- [x] 10.2 Run `scripts/setup-plane.sh` — all states, modules, pages, views created idempotently
- [x] 10.3 Verify Plane workspace shows correct states, modules, pages, and views in UI
- [x] 10.4 Verify work items have native priority values and priority labels are removed
- [x] 10.5 Verify plugin Propose action creates work items with "Backlog" state
