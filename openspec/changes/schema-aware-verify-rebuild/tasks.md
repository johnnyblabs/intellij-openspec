## 1. Mode-aware Verify entry

- [x] 1.1 Resolve the `WorkflowSchemaContext` for the change at the start of Verify (off-EDT, via the Phase 1 service)
- [x] 1.2 For a non-default mode (e.g. `workspace-planning`), produce an "explain and stop" result — no spec-driven scan — and surface it in the report/notification
- [x] 1.3 For `spec-driven` repo-local, proceed to the completeness + correctness/coherence pipeline

## 2. Completeness (local, deterministic gate)

- [x] 2.1 Check that all required artifacts exist and that `tasks.md` has no incomplete `- [ ]` checkboxes
- [x] 2.2 Emit completeness findings; keep these as the deterministic archive gate (CRITICAL when blocking)

## 3. Correctness / coherence (semantic, language-agnostic)

- [x] 3.1 Remove the `.java`-only filter in `VerificationService` (line ~142) and any other file-extension/language gating in the verify path
- [x] 3.2 Delegate the semantic correctness/coherence assessment (delta-spec satisfaction + design coherence) to the AI bridge, language-agnostically
- [x] 3.3 Map AI-surfaced findings to WARNING/SUGGESTION severity (not the archive-blocking CRITICAL)

## 4. Graceful degradation

- [x] 4.1 When no AI provider is configured, still run completeness and report correctness/coherence as "not assessed (AI provider not configured)" — no false pass/fail

## 5. Report wiring

- [x] 5.1 Source `VerificationReport` findings from the new mode-aware pipeline
- [x] 5.2 Preserve report behavior: severity grouping, archive gate on CRITICAL, "All clear — ready to archive" when clean

## 6. Tests

- [x] 6.1 Mode gate: non-default mode → explain-and-stop, no spec-driven findings
- [x] 6.2 Completeness: missing artifact and incomplete tasks each produce findings and gate archive
- [x] 6.3 Language-agnostic: a non-Java (e.g. Kotlin/Go) change is not skewed by `.java` filtering
- [x] 6.4 Degradation: no AI provider → completeness runs, correctness/coherence reported "not assessed"

## 7. Documentation

- [x] 7.1 Update the OpenSpec client coverage matrix (`docs/openspec-support.md`): `verify-change` row from ⚠️ divergent toward aligned, and the `workspace-planning` row to reflect Verify's mode-gating; keep public docs vendor-neutral
