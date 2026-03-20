## Context

The Forgejo Actions workflow (`.forgejo/workflows/build.yaml`) has two jobs: `build` (compile + test) and `verify` (Plugin Verifier). Currently `verify` only runs on `main` and depends on `build` completing first. This means PRs don't get binary compatibility feedback until after merge.

## Goals / Non-Goals

**Goals:**
- Run Plugin Verifier on every push and PR, not just main
- Run verify in parallel with build to reduce total CI time
- Catch IDE compatibility breaks before they reach main

**Non-Goals:**
- Adding new IDE versions to the verification matrix (use `recommended()` as-is)
- Changing the build job or test configuration
- Adding Plugin Verifier to local development workflow

## Decisions

### Run verify in parallel with build
**Decision:** Remove `needs: build` from the verify job.
**Rationale:** `verifyPlugin` runs its own compilation internally — it doesn't need the build job's output. Running in parallel cuts total CI time roughly in half for the verify feedback loop.
**Alternative:** Keep sequential. Rejected because it doubles wait time for no benefit.

### Remove the main-only gate
**Decision:** Remove `if: github.ref == 'refs/heads/main'` from the verify job.
**Rationale:** The whole point is catching breaks before merge. Running only on main defeats the purpose.

## Risks / Trade-offs

- [Longer PR CI time] → Verify adds ~2-3 min per run, but runs in parallel so wall-clock time is similar. Trade-off is acceptable for the safety gained.
- [Runner resource usage] → Two parallel jobs per push. The `java-21` runner handles this — it's already provisioned for concurrent builds.
