## Why

The plugin's Verify is a local, schema-blind heuristic: its correctness check greps only `.java` files for evidence that spec requirements were implemented, so it is wrong on Kotlin / Go / any non-Java project (the GitHub #18 class of bug) and does not reflect OpenSpec's actual `verify-change` workflow or the active schema/mode. With the Phase 1 schema/version-aware foundation in place, Verify can be rebuilt as a faithful, language-agnostic, schema-aware surface. This is Phase 2 of the workflow-fidelity roadmap; see the linked tracker entry.

## What Changes

- Rebuild Verify to drive off the resolved workflow schema context (`openspec status` `actionContext.mode` + `openspec instructions`) instead of assuming a `spec-driven`, repo-local layout.
- **Mode-aware**: for `workspace-planning` mode, explain that repo-local verify does not apply and stop; for `spec-driven`, run verification.
- Split spec-driven verification into two clearly-separated dimensions:
  - **Completeness** — local, fast, deterministic gate: required artifacts present and no incomplete tasks.
  - **Correctness / coherence** — semantic, **delegated to the AI bridge**, language-agnostic.
- **Retire the language-gated heuristic**: remove the `.java`-only codebase scan; semantic correctness is delegated and SHALL NOT gate on source file extension/language.
- Keep the verification report (severity levels, archive gate) but source its findings from the new mode-aware pipeline.

## Capabilities

### Modified Capabilities
- `verify-workflow`: rebuild pre-archive verification to be schema/mode-aware (driven by the resolved workflow schema context), language-agnostic, and to delegate semantic correctness to the AI bridge rather than a Java-only codebase grep.

## Impact

- **Affected code**: `VerificationService` (the verify pipeline and the `.java`-only filter), `OpenSpecVerifyAction`, the `VerificationReport` / `VerificationFinding` models, `VerifyReportDialog`; consumes the Phase 1 `WorkflowSchemaContext`.
- **Behavior**: Verify becomes correct on non-Java projects (addresses the GitHub #18 class of bug) and stops with an explanation on non-default modes rather than producing spec-driven-shaped findings.
- **Dependency**: builds on Phase 1 (schema/version-aware foundation, the `workflow-schema-context` capability).
- **Platform compatibility**: no change — continues to support IntelliJ IDEA 2024.2 and later.
- **Roadmap**: Phase 2 (workflow-surface fidelity); the headline parity fix for `verify-change`.
