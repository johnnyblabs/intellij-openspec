## Context

The WorkflowActionPanel displays a pipeline of artifact chips (proposal → specs → design → tasks) with status from the CLI's `openspec status --json`. The CLI determines "done" by file existence at the artifact's `outputPath`. When changes are created with scaffolding files (e.g., by `/opsx:propose` or other tooling that pre-creates templates), the CLI reports all artifacts as "done" even when files contain only placeholder content like `<!-- Describe the technical approach -->`.

The plugin already has `BuiltInValidator` which reads file content for spec validation, and `ArtifactOrchestrationService` which fetches and caches the DAG. The existing `ArtifactInfo` model carries `id`, `outputPath`, `status`, and `missingDeps`.

## Goals / Non-Goals

**Goals:**
- Detect when artifact files contain scaffolding placeholders vs real content
- Override CLI-reported "done" status to "ready" or "blocked" when content is scaffolding
- Ensure the pipeline and Generate button guide users through each artifact sequentially
- Show what artifact comes next after generating one

**Non-Goals:**
- Modifying the OpenSpec CLI itself
- Validating content quality beyond scaffolding detection (that's the validator's job)
- Changing the artifact DAG structure or dependency order

## Decisions

### 1. Add ScaffoldingDetectionService as a project-level service

A new `ScaffoldingDetectionService` checks whether an artifact file's content is still scaffolding. It uses simple heuristics:
- Strip markdown headings (`# ...`, `## ...`) and HTML comments (`<!-- ... -->`)
- Strip known placeholder lines (`- [ ] Task 1`, `- [ ] Write unit tests`, etc.)
- If remaining content (trimmed) is empty or below a minimum threshold (~20 chars), it's scaffolding

This is intentionally conservative — it only catches obvious scaffolding templates, not low-quality content. The service is stateless and cheap to call.

**Alternative considered:** Regex patterns for specific known templates. Rejected because it's brittle — any change to the scaffolding template would break detection.

### 2. Apply status override in ArtifactOrchestrationService after CLI parsing

After parsing the CLI DAG, the orchestration service calls the scaffolding detector for each "done" artifact. If scaffolding is detected, the artifact status is downgraded:
- If all dependencies are truly done (not scaffolding) → override to READY
- If dependencies are still scaffolding → override to BLOCKED with the scaffolding deps as `missingDeps`

This keeps the override logic centralized and invisible to the rest of the UI code. The pipeline chips, Generate button, and guidance card all work correctly because they read from the corrected DAG.

**Alternative considered:** Override in the UI layer (WorkflowActionPanel). Rejected because the correction should apply everywhere the DAG is consumed, not just one panel.

### 3. Recalculate `isComplete` after overrides

The `ChangeArtifactDag.isComplete` field from the CLI is also wrong when scaffolding exists. After applying overrides, recalculate: `isComplete = all artifacts have status DONE (after override)`.

### 4. Enhance guidance card with next-artifact context

After generating an artifact via clipboard/editor, the guidance card already shows confirmation and output path. Add a line showing what comes next: "Next: Generate design" or "Next: Generate tasks". This uses the corrected DAG to find the next ready artifact after the current one completes.

## Risks / Trade-offs

- [Risk] Scaffolding detection could have false positives on legitimately short files → Mitigation: Use a low threshold (~20 meaningful chars) and only strip known scaffolding patterns (HTML comments, placeholder task items). A real design doc with just "Use REST API" would pass.
- [Risk] The status override disagrees with CLI → This is intentional. The plugin knows more than the CLI because it can read content. The CLI status is still stored; we just overlay our assessment.
- [Trade-off] This adds a file read per "done" artifact on each refresh → These are small markdown files, reads are fast, and the DAG is cached. Acceptable cost for correct status.
