## Context

`VerificationService.verify(...)` runs two gates today. The **mode gate** already consumes `openspec status` via `WorkflowSchemaContextService` (which itself reuses `ArtifactOrchestrationService` so the CLI isn't invoked twice): non-default modes (e.g. `workspace-planning`) explain and stop. The **completeness gate** (`checkCompleteness`, `VerificationService.java:90-130`) does not: it checks `Files.exists` against a hardcoded `List.of("proposal.md", "design.md", "tasks.md")` and regex-parses `tasks.md` checkboxes (`- [ ]` / `- [~]` / `- [x]`).

Meanwhile every other workflow surface — Apply, Continue, `WorkflowActionPanel`, `BulkArchiveDialog` — sources completeness from `ArtifactOrchestrationService.getArtifactStatus(changeName)`, which runs `openspec status --change <name> --json`, parses it via `CliOutputParser.parseChangeStatus` into `ChangeArtifactDag` (`isComplete`, `applyRequires`, `artifacts[]` with `ArtifactStatus`, `actionContext`), applies client-side scaffolding overrides to `isComplete`, and caches the result with cache-fallback on CLI failure.

Consequences of the divergence: Verify assumes a `spec-driven` three-artifact layout (misses the `specs` artifact entirely, and any schema-defined artifact set), and Verify's verdict can disagree with Apply's gate on the same change.

## Goals / Non-Goals

**Goals:**
- Verify's artifact-level completeness findings come from the CLI status DAG — schema-aware, consistent with Apply/Continue.
- Preserve the exact task-checkbox semantics the spec mandates (in-progress `- [~]` counted, reported distinctly, blocking).
- Degrade gracefully to the current filesystem checks when status is unavailable.
- Contract-test the status-driven path against captured real CLI output.

**Non-Goals:**
- No change to the mode gate, the correctness/coherence (AI bridge) dimension, the report dialog, or the archive gate.
- No new CLI parsing or model code — `ChangeArtifactDag` / `CliOutputParser.parseChangeStatus` are reused as-is.
- No surfacing of new status fields beyond artifact statuses / `isComplete` / `applyRequires` (e.g. no `linkedContext` UX).
- No change to `WorkflowSchemaContextService` or its 1.3.0 CLI floor.

## Decisions

1. **Source artifact-level completeness from `ArtifactOrchestrationService.getArtifactStatus(changeName)`, not a fresh CLI call.**
   Rationale: it is the single existing seam — cached, scaffolding-aware, already consumed by Apply/Continue — so Verify inherits identical semantics for free. *Alternative considered:* calling `CliRunner` + `parseChangeStatus` directly inside `VerificationService`; rejected because it would bypass the cache and the scaffolding overrides, reintroducing the Verify-vs-Apply disagreement this change exists to remove.

2. **Split the completeness dimension: status DAG for artifact level, local `tasks.md` parse for checkbox level.**
   The status JSON reports the `tasks` artifact as a single status (`done`/`ready`/`blocked`/…), not per-checkbox state. Fully switching to status would lose the "N incomplete / M in-progress out of T" granularity the spec's partial-task scenarios require. So: non-`DONE` artifacts from `dag.getArtifacts()` become artifact-level findings (mentioning `applyRequires` membership where relevant), while the existing checkbox regex block stays untouched for the task-count finding. *Alternative considered:* dropping checkbox granularity and reporting only the `tasks` artifact status; rejected — it would regress the "Partial tasks count as not-done" requirement.

3. **Use the scaffolding-overridden `dag.isComplete()`, not the raw CLI value.**
   `ArtifactOrchestrationService.applyScaffoldingOverrides` recomputes `isComplete` from artifact statuses. Using the overridden value keeps Verify's verdict byte-consistent with Apply's gate. This is deliberate and documented here; the raw CLI value is not consulted independently.

4. **Fallback shape mirrors the existing mode-gate degrade pattern.**
   When `getArtifactStatus` returns null or throws (CLI missing, below the 1.3.0 floor, malformed output), Verify falls back to the current hardcoded filesystem existence checks — same try/catch philosophy already in `verify()` for context resolution. The fallback is the *old* behavior, so no environment gets worse than today. A finding or log line SHOULD make the degraded source visible rather than silently passing. *Alternative considered:* failing Verify hard when status is unavailable; rejected — Verify must keep working in CLI-less environments, matching the plugin-wide built-in-fallback convention.

5. **Contract fixture must carry `actionContext` and non-done artifacts.**
   The only existing status fixture (`src/test/resources/fixtures/cli/status.json`) has no `actionContext` block, and no fixture in the repo does — so the mode-gate path is currently unit-tested only, and a status-driven completeness test against the existing fixture would be vacuous for the interesting cases. Capture a real `openspec status --change <name> --json` from the pinned CLI (isolated `XDG_DATA_HOME`, sanitized paths) showing at least one non-`DONE` artifact and a populated `actionContext`, and contract-test `parseChangeStatus` + the new completeness mapping against it.

## Risks / Trade-offs

- [Status cache staleness: Verify could report against a cached DAG after on-disk edits] → `getArtifactStatus` refreshes on invocation and only serves cache on CLI failure — the same exposure Apply already accepts; consistent-with-Apply is the goal.
- [Artifact-level findings change wording/count for existing users (e.g. a `specs` artifact now reported missing where it previously wasn't checked)] → this is the fix, not a regression; changelog entry states it plainly.
- [Below-floor CLIs get filesystem-fallback findings that may disagree with a schema-aware CLI's view] → acceptable: fallback equals today's behavior, and the support doc already scopes status semantics to CLI ≥ 1.3.0.
- [Double status invocation per Verify run (mode gate + completeness)] → `WorkflowSchemaContextService` already routes through `ArtifactOrchestrationService`'s cache, so the second consumer hits the cache, not the CLI.

## Open Questions

None — scoping confirmed the seam, the model fields, and the fixture gap; no upstream ambiguity remains.
