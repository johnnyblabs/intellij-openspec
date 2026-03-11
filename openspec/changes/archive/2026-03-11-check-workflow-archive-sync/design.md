# Design: check-workflow-archive-sync

## Context

The proposal asks to "check the workflow" so archive and sync functionality are consistently supported. In this plugin, archive is a cross-cutting operation: it changes OpenSpec filesystem state and can trigger tracker-side lifecycle updates. Sync behavior is currently ambiguous in the proposal, so this design defines a clear architecture for what "sync" means in the plugin context and how it integrates with archive.

Current state and constraints:
- The workflow already includes archive user actions and issue lifecycle hooks (see `openspec/specs/actions/spec.md` and `openspec/specs/issue-tracking/spec.md`).
- Post-archive conventions are documented at project level (see `openspec/specs/plugin-core/spec.md` post-archive rule requirement).
- Existing services are project-scoped; implementation should stay aligned with IntelliJ service patterns.
- The proposal is intentionally brief, so technical boundaries must be explicit before coding.

Stakeholders:
- Plugin users archiving completed changes.
- Maintainers who need predictable sync and archive behavior.
- Teams using Forgejo/Plane integration and expecting lifecycle consistency.

## Goals / Non-Goals

**Goals:**
- Define a deterministic archive workflow that validates preconditions and reports outcome clearly.
- Define sync as an explicit workflow step that reconciles local change/archive state with tracker metadata and panel state.
- Keep archive and sync idempotent where possible so repeated runs do not corrupt state.
- Ensure archive and sync outcomes are visible to UI components (workflow panel/change selector) and tracker integrations.
- Preserve compatibility with existing OpenSpec artifact structure and post-archive conventions.

**Non-Goals:**
- Redesigning the entire OpenSpec workflow UX.
- Adding new external dependencies.
- Rewriting tracker providers (Forgejo/Plane) beyond required archive/sync lifecycle integration points.
- Implementing bidirectional conflict resolution against arbitrary remote systems in this change.

## Decisions

### Decision 1: Treat archive and sync as separate but chained operations

**Choice:**
Archive remains the primary user action; sync runs as a follow-up phase (automatic after successful archive, and callable manually later).

**Rationale:**
- Archive and sync have different failure domains (filesystem vs tracker/network).
- Separating phases allows partial success reporting (archive done, sync failed) without rolling back valid archive operations.
- Keeps behavior understandable for users and maintainers.

**Alternatives considered:**
- Single atomic archive+sync transaction: rejected because external systems are not transactional with local filesystem changes.
- Sync-only manual flow: rejected because users expect archive action to complete end-to-end lifecycle work.

### Decision 2: Introduce a dedicated service boundary for sync orchestration

**Choice:**
Add a dedicated sync orchestration service (or equivalent façade) invoked by archive and refresh-related workflows, rather than embedding sync logic directly inside archive action handlers.

**Rationale:**
- Centralizes sync policy and error handling.
- Avoids duplicated logic across action entry points.
- Improves testability by isolating orchestration from UI action classes.

**Alternatives considered:**
- Keep sync inside action classes: rejected due to coupling and weaker test coverage.
- Add sync logic directly to each tracker service: rejected because cross-tracker workflow rules would become fragmented.

### Decision 3: Define sync scope as workflow-state reconciliation, not full remote mirroring

**Choice:**
Sync will reconcile only required workflow artifacts and tracking metadata needed by plugin behavior (active/archived change state, linked tracker lifecycle updates, and UI refresh triggers).

**Rationale:**
- Matches proposal intent while controlling complexity.
- Avoids introducing broad remote data synchronization semantics.
- Keeps scope aligned with current OpenSpec plugin responsibilities.

**Alternatives considered:**
- Full two-way remote mirroring: rejected as high complexity and outside current plugin scope.
- No explicit sync definition: rejected because ambiguity causes inconsistent implementations.

### Decision 4: Prefer compensating behavior over rollback for post-archive sync failure

**Choice:**
If archive succeeds but sync fails, persist archive result and surface actionable recovery (notification + manual sync path), rather than attempting archive rollback.

**Rationale:**
- Rolling back filesystem archive after external side effects can create inconsistent states.
- Compensation with explicit retry is safer and operationally clearer.

**Alternatives considered:**
- Hard rollback of archive on sync error: rejected due to non-transactional side effects and high risk of data inconsistency.
- Silent sync failure: rejected because it hides lifecycle drift.

## Risks / Trade-offs

- [Archive succeeds but tracker sync fails] -> Mitigation: mark sync as failed state, show recovery guidance, provide retryable manual sync action.
- [Duplicate lifecycle updates on retries] -> Mitigation: enforce idempotent tracker update checks using change/tracker metadata before issuing updates.
- [UI shows stale change state after archive/sync] -> Mitigation: publish explicit refresh events for workflow panel and change selector after each phase.
- [Ambiguous ownership of sync logic across services] -> Mitigation: define single orchestration entry point and keep provider services focused on provider-specific calls.
- [User confusion about partial success] -> Mitigation: use distinct user-facing messages for archive success, sync success, and sync failure with next action.

## Migration Plan

1. Finalize capability/spec deltas defining archive and sync behavioral requirements.
2. Add sync orchestration boundary and integrate it into archive action flow behind existing service interfaces.
3. Wire tracker lifecycle providers to support idempotent archive-related sync updates.
4. Add UI refresh hooks and user notifications for archive/sync phase outcomes.
5. Add tests:
   - archive success + sync success
   - archive success + sync failure + retry
   - repeated sync calls remain idempotent
6. Roll out with existing archive path preserved; enable sync chaining by default once validation passes.

Rollback strategy:
- If regressions occur, disable sync chaining and keep existing archive behavior active.
- Revert orchestration integration while retaining non-breaking test scaffolding for later reintroduction.

## Open Questions

- Should sync run automatically only after archive, or also on plugin startup/refresh for drift correction?
- What is the authoritative source when local metadata and tracker state disagree during sync?
- Do we need a visible "last sync status" indicator in the workflow panel for operational clarity?
- Should sync failures block post-archive guidance completion, or remain non-blocking with warnings?
- Is a dedicated manual "Sync" action required in menu and tool window for explicit recovery?
