## Why

Plugin Verifier currently only runs on `main` after merge. Binary compatibility breaks from new IDE versions are caught too late — after the code is already merged. Running verification on every push and PR catches these issues before they reach `main`.

## What Changes

- Move `verifyPlugin` from main-only to run on all pushes and PRs (remove the `if: github.ref == 'refs/heads/main'` gate)
- Run verify in parallel with build (remove `needs: build` dependency) to speed up feedback
- Add verify failure as a PR status check so incompatible code can't merge

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

- `ci`: Change Plugin Verifier from main-only to every push/PR, run in parallel with build

## Impact

- `.forgejo/workflows/build.yaml` — modify `verify` job conditions and dependencies
