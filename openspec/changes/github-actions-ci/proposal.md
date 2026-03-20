## Why

The plugin is being published to GitHub (`johnnyblabs/intellij-openspec`) as an open-source project. External contributors will submit PRs on GitHub, not Forgejo. Without CI on GitHub, there's no automated validation of contributions, no build status badge, and no way to enforce quality gates on public PRs. Forgejo CI remains the primary pipeline for signing and Marketplace publishing.

## What Changes

- Add a GitHub Actions workflow that builds and tests the plugin on push/PR to main
- Add Plugin Verifier step to validate binary compatibility
- Configure Gradle caching for faster CI runs
- Add plugin signing and artifact upload on main branch pushes (requires duplicating signing secrets to GitHub)
- Add build status badge to README

## Capabilities

### New Capabilities

_None_ — this is a CI/infrastructure change, not a functional change.

### Modified Capabilities

_None_ — no spec-level behavior changes.

## Impact

- `.github/workflows/build.yml` — new GitHub Actions workflow
- `README.md` — build status badge
- GitHub repository secrets — `PLUGIN_SIGNING_KEY`, `PLUGIN_SIGNING_CERTIFICATE`, `PLUGIN_SIGNING_KEY_PASSWORD` must be added
- Decision: whether GitHub CI fully mirrors Forgejo or is a lighter subset
