## Why

OpenSpec CLI **1.17.0** is the new minor line, and the plugin should declare it supported and target it. 1.17.0 changes only how `openspec update` cleans up old generated files. Every command, flag, JSON shape, schema, validation rule, workflow, workflow template and AI-tool registry entry the plugin parses is unchanged from 1.16.1. This change is the routine target bump: real 1.17.0 contract fixtures, extended version guards and an updated supported range. It is tracked in the linked tracker entry.

## What Changes

- **Declare the 1.17.x line supported** and bump the plugin's target version from `1.16.1` to `1.17.0`.
- **Capture real 1.17.0 contract fixtures** under `fixtures/cli/1.17.0/`, following the recipe in the fixtures README:
  - the parity-corpus twins (default and `--strict`) and `version.txt`;
  - the AI-tool registry, the detect fixtures and `init --help`;
  - the workflow ids and the design and specs instructions;
  - the target-scoped explore, explore-context, workflow-invocation and update-dry-run fixtures.
- **Prove the parsed surfaces are unchanged.** Each capture is compared with its 1.16.1 twin. Any difference other than the version number stops the change for a decision.
- **Extend the version guards** so the floor and at-least assertions include `1.17.0`, and add an ordering pair showing `1.17.0` sorts above `1.9.0`.
- **Adopt-sync:** with the local CLI at 1.17.0, run `openspec update`, review the diff, and restore any customizations from git.
- **Update the documented supported range** to `1.3.x`–`1.17.x` in the user-facing docs and the changelog.
- **Verdict-parity anchors stay put.** Default stays `1.8.0`, strict stays `1.6.0`, and the archive-drop divergence stays pinned from `1.14.2`.
- **Out of scope**, each its own tracker entry:
  - the update preview's handling of 1.17's unasked removal of unedited legacy files and the new `earlier-release` group;
  - support for the 2.x line.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: the supported-versions requirement gains the 1.17.x scenario, and the supported version set extends to `1.17.x`.

## Impact

- `gradle.properties` (the target version), `fixtures/cli/1.17.0/`, the version-guard tests and the contract tests that read target-scoped fixtures.
- The regenerated OpenSpec skills, which carry the new `generatedBy` stamp.
- `docs/openspec-support.md`, `docs/feature-comparison-matrix.md` and `CHANGELOG.md`.
- No plugin behaviour changes. Platform compatibility is unaffected: the plugin still supports IntelliJ IDEA 2024.2 and later.
