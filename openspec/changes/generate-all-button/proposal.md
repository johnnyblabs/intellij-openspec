## Why

The current workflow requires clicking "Generate" once per artifact — four clicks with waiting in between for a typical spec-driven change (proposal, design, specs, tasks). For Direct API users, there's no reason they can't kick off the entire pipeline and watch it complete autonomously. A "Generate All" button is the hero feature that makes the plugin feel like magic: describe what you want, click once, and all artifacts materialize in dependency order.

## What Changes

- Add a "Generate All" button to the WorkflowActionPanel that chains all remaining artifacts through the Direct API in sequence
- Add progress feedback: pipeline chips update in real-time as each artifact completes, with a progress label ("Generating design... 2/4")
- Add cancellation support: user can cancel mid-chain, keeping already-completed artifacts
- Add error handling: if one artifact fails, stop the chain, report the error, and leave completed artifacts intact
- Extend ArtifactOrchestrationService with a method to generate all remaining artifacts in dependency order
- "Generate All" is only available when Direct API is configured — clipboard/editor users continue with the existing one-at-a-time flow

## Capabilities

### New Capabilities
- `generate-all`: Orchestrated sequential generation of all remaining artifacts via Direct API with progress, cancellation, and error recovery

### Modified Capabilities
- `workflow-panel`: Add Generate All button alongside existing Generate button, update pipeline visualization to show real-time progress during generation chain

## Impact

- **WorkflowActionPanel**: New button, progress state, cancel action
- **ArtifactOrchestrationService**: New `generateAll()` orchestration method
- **DirectApiService**: No changes needed (already supports single artifact generation)
- **ChangeArtifactDag**: May need helper to get all remaining artifacts in order
- **No breaking changes**: Existing single-generate flow is unchanged
