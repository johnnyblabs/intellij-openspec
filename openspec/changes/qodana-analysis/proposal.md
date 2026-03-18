## Why

Qodana is JetBrains' static analysis tool, free for plugin developers, tuned specifically for IntelliJ platform code. It catches deprecated API usage, potential null issues, IntelliJ API misuse, and general Java quality issues that generic linters miss. Adding it to CI provides continuous quality feedback with zero ongoing cost.

## What Changes

- Add a `qodana.yaml` configuration file scoped to the plugin source
- Add a Qodana analysis job to the Forgejo Actions CI workflow
- Configure a baseline so existing issues don't block PRs (only new issues fail)

## Capabilities

### New Capabilities

- `qodana`: Qodana static analysis configuration and CI integration

### Modified Capabilities

- `ci`: Add Qodana analysis job to the build workflow

## Impact

- `qodana.yaml` — new config file at project root
- `.forgejo/workflows/build.yaml` — new `qodana` job
- Runner requirements — Qodana runs as a Docker container or CLI tool; needs to be available on the `java-21` runner or run as a separate job with Docker support
