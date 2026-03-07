## Why

The CLI determines artifact status by file existence — if `design.md` exists, it's "done". But when a change is created with scaffolding files (placeholder templates), the CLI reports them as complete even though they contain no real content. This causes the pipeline to show `✓ design → ✓ tasks` when both are still empty templates like `<!-- Describe the technical approach -->` and `- [ ] Task 1`. The user sees "All complete" and thinks the change is ready to apply, but design and tasks have no usable content.

## What Changes

- Add a scaffolding detection service that checks whether artifact files contain real content or are still placeholder templates
- Override CLI-reported artifact status in the plugin: downgrade "done" to "ready" or "blocked" when content is still scaffolding
- Update the pipeline visualization and Generate button to reflect content-aware status, so users are guided through generating each artifact sequentially
- Update the guidance card to show what was just generated and what artifact comes next in the pipeline

## Capabilities

### New Capabilities
- `scaffolding-detection`: Service that detects whether artifact files contain scaffolding placeholder content vs real authored/generated content

### Modified Capabilities
- `workflow-panel`: Pipeline status uses content-aware artifact status instead of raw CLI status; guidance card shows next artifact context after generation

## Impact

- New `ScaffoldingDetectionService.java` — content analysis for artifact files
- `ArtifactOrchestrationService.java` — applies scaffolding override to CLI-reported DAG status
- `WorkflowActionPanel.java` — pipeline and Generate button reflect corrected status; guidance card shows next-artifact context
- `ChangeArtifactDag.java` or `ArtifactInfo.java` — may need a status override mechanism
