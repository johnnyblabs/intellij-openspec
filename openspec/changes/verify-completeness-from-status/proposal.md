## Why

Verify's completeness gate is the last workflow surface that ignores `openspec status`: it checks a hardcoded three-artifact list (`proposal.md`, `design.md`, `tasks.md`) against the filesystem, silently assuming a `spec-driven` layout. Apply, Continue, the workflow panel, and bulk archive all already drive off the CLI's status DAG (`ArtifactOrchestrationService`), so Verify can disagree with them — e.g. flag a `specs`-requiring change as complete, or miss schema-defined artifacts beyond the hardcoded three. Closing this makes Verify schema-aware and retires the "status: not yet used by Verify" partial-support row in the version support doc.

## What Changes

- Verify's **artifact-level completeness findings** are sourced from `openspec status --change <name> --json` via the existing `ArtifactOrchestrationService.getArtifactStatus(...)` — the schema's own artifact set and statuses (`done`/`ready`/`blocked`/...) replace the hardcoded three-file existence check. Verify's completeness verdict thereby matches Apply's gate (including the client-side scaffolding overrides on `isComplete`).
- **Task-checkbox granularity stays locally parsed.** The status DAG reports the `tasks` artifact as a single status, not per-checkbox state, so the `- [ ]` / `- [~]` / `- [x]` counting (and the distinct in-progress reporting) continues to read `tasks.md` directly. This split — status for artifact level, local parse for checkbox level — is deliberate.
- **Graceful degradation:** when the CLI is unavailable or below the 1.3.0 floor, Verify falls back to the current deterministic filesystem checks, mirroring the existing degrade pattern already used for the mode gate.
- The mode gate (`actionContext.mode`) is untouched — it already consumes status.
- A **captured real `openspec status --json` fixture** carrying an `actionContext` block and non-done artifacts is added under `src/test/resources/fixtures/cli/`, contract-testing the new path (no fixture today carries `actionContext`).
- Doc surfaces updated: the `status` and `verify-change` rows in `docs/openspec-support.md`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `verify-workflow`: the completeness-check requirement changes from "locally and deterministically, that all required artifacts exist" to sourcing the artifact-level check from the CLI status DAG (schema-aware), with the filesystem check demoted to an explicit fallback; the partial-task (`- [~]`) semantics are preserved unchanged.

## Impact

- **Code:** `VerificationService.checkCompleteness(...)` reworked to consume `ChangeArtifactDag` from `ArtifactOrchestrationService` (already a project service; no new parser or model code). One production file heavily touched.
- **Tests:** `VerificationService` unit tests updated; one new captured CLI fixture plus a contract test for the status-driven path.
- **Docs:** two rows in `docs/openspec-support.md`.
- **Platform compatibility:** none — no IntelliJ platform API surface changes; IntelliJ IDEA 2024.2+ support unaffected.
- **Tracker:** tracked in the project tracker; identifiers live in this change's local tracking sidecar per repo convention.
