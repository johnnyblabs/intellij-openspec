# Explore context fixture (OpenSpec CLI 1.3.1)

`status-add-mfa.json` is `openspec status --change add-mfa --json` from CLI 1.3.1, the last minor before `artifactPaths` (added in 1.4.0). It is captured with `scripts/capture-explore-context-fixtures.py --basic` (see the script for the `npx` recipe) and tests the fallback when the status has no artifact paths.
