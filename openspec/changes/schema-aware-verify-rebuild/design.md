## Context

The plugin's value is fidelity to the OpenSpec client. Today `VerificationService.verify(changeName)` is schema-blind and language-gated: its correctness check greps **only `.java` files** (`VerificationService.java:142`) for evidence that spec requirements were implemented, so it mis-reports on Kotlin / Go / any non-Java project (the GitHub #18 class of bug) and ignores `actionContext.mode` entirely. Phase 1 added the resolved `WorkflowSchemaContext` (active schema, `actionContext.mode`, version axes). Phase 2 rebuilds Verify on that foundation to match OpenSpec's `verify-change`: mode-gated, with a deterministic completeness gate plus a semantic, language-agnostic correctness/coherence check delegated to the AI bridge.

## Goals / Non-Goals

**Goals:**
- Verify reads the resolved `WorkflowSchemaContext` and branches on `actionContext.mode`.
- Spec-driven: a fast, deterministic **completeness** gate (artifacts present, no incomplete tasks) plus **correctness/coherence** delegated to the AI bridge.
- **Language-agnostic** — remove the `.java`-only filter; no file-extension gating.

**Non-Goals:**
- Workspace/coordination verification (Phase 3) — `workspace-planning` mode simply explains and stops here.
- Reintroducing any `@spec` annotation or coverage scorecard (removed; off-model).
- Reworking the report dialog UX beyond sourcing findings from the new pipeline.

## Decisions

- **D1 — Mode gate via the resolved context.** Use `WorkflowSchemaContext.isNonDefaultMode()`; for `workspace-planning`, explain that repo-local verify does not apply and stop. *Alternative:* run a generic verify anyway — rejected; repo-local verify semantics don't apply, and the faithful behavior is to stop with an explanation.
- **D2 — Completeness stays local and deterministic** (required artifacts present; `tasks.md` has no `- [ ]`). Fast, no AI, and a reliable archive gate. *Alternative:* delegate everything to AI — rejected; completeness is cheap, deterministic, and the dependable gate.
- **D3 — Correctness/coherence delegated to the AI bridge, language-agnostic.** Drop the code grep and its `.java` filter; assess semantically via the AI delivery path. *Alternative:* keep a grep but generalize file extensions — rejected; still a brittle heuristic, and semantic inference matches the client's own model.
- **D4 — Graceful degradation when no AI provider is configured.** Completeness still runs and gates; correctness/coherence is reported as "not assessed (AI provider not configured)" rather than a false pass or fail.

## Risks / Trade-offs

- AI-delegated correctness is non-deterministic → keep completeness as the deterministic gate; treat AI-surfaced correctness findings as WARNING/SUGGESTION, not the archive-blocking CRITICAL (only completeness failures gate).
- AI semantic-check latency → run on the existing background task; completeness returns fast and renders first.
- No AI provider configured → degrade per D4; never block on it.

## Migration Plan

Additive within the verify pipeline; remove the `.java` filter. No data migration. Rollback is reverting the `VerificationService` changes.

## Open Questions

- Final severity mapping for AI-surfaced correctness findings (WARNING vs SUGGESTION) — settle during implementation.
