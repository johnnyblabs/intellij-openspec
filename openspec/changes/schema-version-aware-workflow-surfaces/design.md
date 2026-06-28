## Context

The plugin is a companion to the OpenSpec client; its value is fidelity to that client. Today the workflow surfaces (propose dialog, action panel, pipeline view, status strip) assume a `spec-driven`, repo-local layout. The client already exposes the real shape via `openspec status --json` → `actionContext` (`mode`, `sourceOfTruth`, `allowedEditRoots`) from the 1.3 floor onward, and via `openspec instructions --json`. Separately, two independent version axes exist and are easy to conflate: the **CLI version** (floor 1.3, baseline 1.4 — gates which commands/schemas exist) and the **config-format version** (`openspec/config.yaml` `version:`, stable across CLI lines and plugin-internally required). The plugin already resolves an effective version and guards on CLI version in `schema-management`. What's missing is a single resolved *mode/schema context* that workflow surfaces consume instead of hard-coding the spec-driven assumption.

## Goals / Non-Goals

**Goals:**
- A single resolved **workflow schema context** (active schema, `actionContext.mode`, `sourceOfTruth`, both version axes) as the source of truth for mode- and version-dependent behavior.
- Workflow surfaces consult that context and adapt to the active mode.
- Preserve current behavior for the default spec-driven repo-local project; graceful built-in fallback when the CLI is absent or below the floor.

**Non-Goals:**
- Rebuilding Verify to be schema-aware/semantic (Phase 2, `verify-workflow`).
- Surfacing the 1.4 coordination layers — `workspace` / `context-store` / `initiative` (Phase 3).
- Authoring/forking schemas (already covered by `schema-management`).

## Decisions

- **D1 — Derive context from `openspec status --json` `actionContext`, not filesystem heuristics.** Reading the client's own answer is the fidelity-preserving choice. *Alternative:* infer layout from directory shape — rejected; that fragility is the exact bug being fixed.
- **D2 — Keep the CLI-version and config-format-version axes separate in the model.** *Alternative:* a single version field — rejected; conflation is a known incident, and the config `version:` is plugin-internally required for self-validation while the CLI version gates feature availability.
- **D3 — Built-in fallback assumes spec-driven repo-local when the CLI is unavailable or below the 1.3 floor.** Preserves today's behavior for the common case where `actionContext` is unavailable.
- **D4 — Cache the resolved context per change selection; invalidate on selection change and on propose/apply/archive**, mirroring `schema-management`'s cache discipline, to avoid per-render CLI calls on the EDT.

## Risks / Trade-offs

- `actionContext` shape varies across CLI versions → gate on 1.3+ where it exists; fall back below the floor.
- Over-adapting surfaces to modes not yet fully supported → Phase 1 only *reads and branches*; full non-spec-driven UX is later phases. Surfaces SHALL degrade informatively (reflect the mode), not pretend spec-driven affordances apply.
- Hidden coupling: config `version:` is plugin-internal-required → keep reading it for effective-version resolution; do not drop it.

## Migration Plan

Additive; no data migration. The default spec-driven repo-local path is unchanged. Rollback is reverting the context-consumer wiring on the workflow surfaces.

## Open Questions

- Minimum set of modes to branch on in this phase — `spec-driven` vs `workspace-planning` at minimum; richer per-mode UX is deferred to later phases.
