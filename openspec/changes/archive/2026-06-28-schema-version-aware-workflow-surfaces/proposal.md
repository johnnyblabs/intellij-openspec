## Why

The plugin's workflow surfaces assume a `spec-driven`, repo-local layout, so they misbehave under other OpenSpec schemas/modes (e.g. `workspace-planning`) and don't distinguish the CLI-version axis from the config-format-version axis. This change establishes a schema/version-aware foundation so that later fidelity work — a faithful Verify and the 1.4 coordination layers — builds on accurate mode and version context rather than hard-coded assumptions. It implements Phase 1 of the workflow-fidelity roadmap; see the linked tracker entry.

## What Changes

- Add a resolved **workflow schema context** derived from `openspec status --json` / `openspec instructions --json` — surfacing `actionContext.mode`, `sourceOfTruth`, the active schema, and the two version axes — as a single source of truth.
- Workflow surfaces (propose, pipeline/action panel, status strip) SHALL consult this context and adapt to the active mode instead of assuming a `spec-driven` / repo-local layout.
- Distinguish the **CLI version** axis (floor 1.3, baseline 1.4) from the **config-format version** axis, and stop conflating them in version-dependent behavior.
- Preserve current behavior for the default spec-driven, repo-local project, and keep graceful built-in fallback when the CLI is absent or below the floor.

## Capabilities

### New Capabilities
- `workflow-schema-context`: a resolved, cached representation of the active OpenSpec mode/schema and version axes (derived from `openspec status` / `openspec instructions`), consumed by workflow surfaces as the single source of truth for mode- and version-dependent behavior.

### Modified Capabilities
- `workflow`: workflow surfaces (propose, action panel, pipeline, status strip) SHALL consult the schema context and adapt to `actionContext.mode` rather than assuming a spec-driven, repo-local layout.

## Impact

- **Affected code**: the `openspec status` / `instructions` reading path and the workflow tool-window surfaces (propose dialog, action panel, pipeline view, status strip); version resolution (the effective-version logic and the config-format `version:` field).
- **CLI contract**: relies on `openspec status --json` `actionContext` (1.3+) and `openspec instructions --json`; built-in fallback when the CLI is unavailable or below the floor.
- **Platform compatibility**: no change — continues to support IntelliJ IDEA 2024.2 and later.
- **Roadmap**: foundation that unblocks Phase 2 (faithful Verify) and Phase 3 (1.4 coordination layers).
