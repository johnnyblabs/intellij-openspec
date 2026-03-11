## 1. Sync Orchestration Foundation

- [x] 1.1 Add a dedicated sync orchestration service boundary for archive follow-up reconciliation.
- [x] 1.2 Define orchestration result states for sync success, sync failure, and retry eligibility.
- [x] 1.3 Wire orchestration entry points so archive can invoke sync and manual retry can invoke sync without re-archiving.

## 2. Archive Action Chaining and Outcome Handling

- [x] 2.1 Update Archive action flow to trigger sync only after archive filesystem success.
- [x] 2.2 Ensure Archive action skips sync when archive fails and reports archive failure directly.
- [x] 2.3 Add distinct user-facing outcomes for archive plus sync success and archive success with sync failure.

## 3. Tracker Sync Reconciliation and Idempotency

- [x] 3.1 Implement idempotent Forgejo post-archive sync updates to avoid duplicate close, comment, and label operations.
- [x] 3.2 Implement idempotent Plane post-archive sync updates to avoid duplicate terminal state transitions.
- [x] 3.3 Handle missing tracking metadata by skipping only missing tracker updates while completing remaining sync work.
- [x] 3.4 Mark tracker sync failures as recoverable and preserve archived change state.

## 4. Workflow Panel Recovery UX and Refresh

- [x] 4.1 Surface separate archive and sync outcome states in the Workflow Action Panel.
- [x] 4.2 Add a sync retry action in the panel when sync fails after archive.
- [x] 4.3 Refresh change selector and workflow panel state after archive completion, sync completion, and sync retry completion.

## 5. Validation and Regression Tests

- [x] 5.1 Add tests for archive success followed by sync success.
- [x] 5.2 Add tests for archive success followed by sync failure and successful retry.
- [x] 5.3 Add tests proving repeated sync runs remain idempotent for Forgejo and Plane.
- [x] 5.4 Add tests for missing tracking metadata during sync reconciliation.
- [x] 5.5 Run the full test suite and confirm no regressions in existing archive workflow behavior.
