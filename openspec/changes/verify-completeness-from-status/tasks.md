## 1. Contract fixture (do first — per the contract-test rule)

- [x] 1.1 Capture a real `openspec status --change <name> --json` fixture from the pinned CLI using an isolated `XDG_DATA_HOME` scratch project, showing at least one non-`DONE` artifact and a populated `actionContext` block; sanitize machine-specific paths and commit as `src/test/resources/fixtures/cli/status-with-context.json`
- [x] 1.2 Add a contract test (alongside `CliContractTest`) asserting `CliOutputParser.parseChangeStatus` reads the new fixture's `artifacts[]` statuses, `isComplete`, `applyRequires`, and `actionContext` fields correctly

## 2. Status-driven completeness in VerificationService

- [x] 2.1 In `VerificationService.checkCompleteness(...)`, resolve `ArtifactOrchestrationService.getArtifactStatus(changeName)` and, when a DAG is returned, emit artifact-level completeness findings from `dag.getArtifacts()` (each non-`DONE` artifact → finding, noting `applyRequires` membership) instead of the hardcoded three-file `Files.exists` loop
- [x] 2.2 Keep the `tasks.md` checkbox regex block unchanged and run it in both the status-driven and fallback paths, preserving the distinct in-progress (`- [~]`) reporting
- [x] 2.3 Wrap the status resolution in the same degrade pattern `verify()` already uses for context resolution: on null/exception, fall back to the existing filesystem existence checks and make the degraded source visible (log or SUGGESTION finding), never failing Verify solely for missing status
- [x] 2.4 Verify no duplicate CLI invocation per run — the mode gate's `WorkflowSchemaContextService` and the new completeness call must share the `ArtifactOrchestrationService` cache

## 3. Tests

- [x] 3.1 Update/extend the `VerificationService` unit tests: status DAG with a non-done artifact yields an artifact-level finding; fully-done DAG plus not-done checkboxes still blocks on tasks; null DAG exercises the filesystem fallback
- [x] 3.2 Confirm the JaCoCo coverage floor still passes (`./gradlew build`) and ratchet if headroom grew

## 4. Spec sync and docs

- [x] 4.1 Validate the change (`openspec validate` clean; delta spec scenarios match implemented behavior)
- [x] 4.2 Update `docs/openspec-support.md`: the `status` row (drop "not yet by Verify") and the `verify-change` row note; keep version-facts links intact
- [x] 4.3 Add the user-facing CHANGELOG entry under Unreleased (schema-aware Verify completeness; vendor-neutral wording)
