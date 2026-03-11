## Context

The Plane workspace at `http://plane.geek` (workspace: "openspec") has a single project created by `scripts/setup-plane.sh` ("OpenSpec Plugin" / OSP). The user reports seeing 2 projects — the second was likely created manually or from an earlier script run. The workspace currently uses only basic features: cycles (3, mapped from milestones), labels (19), and work items (40). Advanced features like custom states, modules, pages, views, native priority, and estimates are unused.

The setup scripts (`setup-plane.sh`, `plane-api.sh`) are idempotent and use Plane's REST API v1. The plugin's `PlaneService` was just added and resolves states by name at runtime.

## Goals / Non-Goals

**Goals:**
- Audit the workspace, identify and remove the duplicate project
- Configure a meaningful workflow with custom states (Backlog → Todo → In Progress → In Review → Done → Cancelled)
- Create modules to organize work by feature area
- Create project pages for key documentation
- Create saved views for common filtered perspectives
- Migrate from `priority:*` labels to Plane's native priority field
- Enable and configure estimates (story points)
- Update setup scripts to configure all features idempotently
- Update `sync-status.sh` to use proper state transitions
- Update `PlaneService` to use configured state names

**Non-Goals:**
- Changing Forgejo issue structure (Forgejo stays as-is)
- Implementing two-way sync between Plane and Forgejo
- Adding new plugin UI for Plane features (beyond what `PlaneService` already does)
- Setting up Plane webhooks or real-time integrations
- Migrating away from Forgejo — both systems stay in use

## Decisions

### 1. Duplicate project cleanup: Manual via Plane UI

**Decision:** Document the cleanup steps rather than scripting deletion. The user should manually identify and delete the duplicate project through Plane's UI to avoid accidentally deleting the wrong one.

**Rationale:** Project deletion is destructive and irreversible. A script can't reliably distinguish the "real" project from the duplicate if they have similar names. Manual verification is safer.

### 2. Workflow states: 6-state pipeline

**Decision:** Configure these states (in order):
| State | Group | Description |
|-------|-------|-------------|
| Backlog | backlog | Not yet scheduled |
| Todo | unstarted | Scheduled for a cycle |
| In Progress | started | Actively being worked on |
| In Review | started | Implementation complete, under review |
| Done | completed | Finished and verified |
| Cancelled | cancelled | Won't do / superseded |

**Rationale:** Maps to the OpenSpec lifecycle: Propose → Backlog/Todo, Apply → In Progress, Archive → Done. The "In Review" state bridges the gap between Apply and Archive. "Cancelled" handles abandoned changes. These groups map to Plane's built-in state groups for proper progress tracking.

**Alternative considered:** Simpler 4-state (Todo, In Progress, Done, Cancelled) — rejected because "Backlog" and "In Review" add meaningful status visibility.

### 3. Modules: 6 feature areas

**Decision:** Create modules matching the major code/spec domains:
- **Core** — Plugin framework, settings, services
- **UI** — Tool window, workflow panel, tree, editor
- **AI Integration** — Direct API, providers, delivery
- **Validation** — Inspections, spec parsing, annotators
- **Documentation** — Getting started, onboarding, help
- **Infrastructure** — CLI integration, scaffolding, setup scripts

**Rationale:** Modules in Plane act like epics/components. These align with the existing label categories and spec domains, giving a higher-level view than individual work items.

### 4. Pages: 3 key documents

**Decision:** Create these project pages:
- **Architecture Overview** — High-level plugin architecture, service registry, key patterns
- **Release Notes** — Per-version changelog (v0.1.0, v0.2.0, v0.3.0)
- **Development Guide** — How to set up, build, test, and contribute

**Rationale:** Keeps essential project knowledge in Plane alongside the work items, rather than scattered in README files or wikis.

### 5. Views: 4 saved filters

**Decision:** Create saved views:
- **Current Cycle** — Items in the active cycle, sorted by priority
- **High Priority** — All items with priority Urgent or High
- **By Module** — Grouped by module for component-level planning
- **Blocked / Stale** — Items not updated in 14+ days

**Rationale:** Views provide quick access to common questions ("what's important?", "what's stuck?") without rebuilding filters each time.

### 6. Priority migration: Native field + remove labels

**Decision:** Set the native `priority` field on all work items based on their `priority:*` labels, then remove the 3 priority labels (`priority:high`, `priority:medium`, `priority:low`).

Priority mapping: `priority:high` → "high", `priority:medium` → "medium", `priority:low` → "low", no label → "none".

**Rationale:** Plane's native priority field integrates with sorting, filtering, and views. Labels as priority markers are a workaround from initial setup.

### 7. Estimates: Story points with Fibonacci sequence

**Decision:** Enable estimates with Fibonacci values (1, 2, 3, 5, 8, 13). Don't estimate existing items — apply estimates going forward as items are groomed.

**Rationale:** Fibonacci is the standard agile estimation scale. Retrofitting estimates on 40 existing items adds no value since most are already in progress or done.

### 8. Script updates: Extend existing setup pattern

**Decision:** Add new API functions to `plane-api.sh` for states, modules, pages, and views. Add configuration calls to `setup-plane.sh`. All operations remain idempotent.

**Rationale:** Follows the existing pattern. Running the script again on a configured workspace should be a no-op.

## Risks / Trade-offs

- **[Duplicate project data loss]** → User must manually verify which project is the duplicate before deleting. Document the identification steps clearly.
- **[State migration]** → Existing work items have default states. Need to map them to new custom states. Items already marked "Done" via sync script should stay Done.
- **[API coverage]** → Plane's v1 API may not support all features (pages, views, estimates). Need to verify endpoint availability. Fallback: document manual configuration steps for unsupported features.
- **[Priority migration irreversibility]** → Once priority labels are removed, they can't be easily recreated with the same IDs. Mitigation: migrate priority values first, verify, then remove labels in a separate step.
