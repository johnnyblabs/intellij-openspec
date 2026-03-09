## Context

The WorkflowActionPanel currently has a single "Generate [artifact]" button that generates one artifact at a time via the user's preferred delivery method (Direct API, Clipboard, or Editor Tab). For Direct API users, after each generation completes the panel refreshes and shows the next artifact — but the user must click again for each one. The full pipeline for a spec-driven change is: proposal → design → specs → tasks.

The ArtifactOrchestrationService already understands the DAG, can fetch instructions, and the DirectApiService can generate content. The building blocks exist — we need to chain them.

## Goals / Non-Goals

**Goals:**
1. Add a "Generate All" button that chains all remaining artifacts through Direct API
2. Show real-time progress (pipeline chips update, progress label)
3. Support cancellation mid-chain
4. Handle errors gracefully — stop chain, report, keep completed work
5. Keep the existing single-generate flow unchanged

**Non-Goals:**
- Generate All for clipboard/editor delivery (requires human in the loop — can't automate)
- Parallel artifact generation (artifacts depend on each other in the DAG)
- Retry failed artifacts automatically (user should review the error first)
- Streaming API responses (separate feature, tracked separately)

## Decisions

### Decision 1: Generate All button placement and behavior

**Choice:** Add "Generate All" as a second button next to the existing "Generate [artifact]" button. Only enabled when Direct API is configured and there are 2+ remaining artifacts.

**Rationale:** Users who want the step-by-step experience keep it. "Generate All" is additive. When only 1 artifact remains, the regular Generate button is sufficient. When Direct API isn't configured, the button is hidden (not just disabled) to avoid confusion.

**Alternative rejected:** Replacing the Generate button with a mode toggle — adds complexity, changes the existing UX.

### Decision 2: Orchestration loop architecture

**Choice:** Add a `generateAllRemaining()` method to ArtifactOrchestrationService that returns a cancellable orchestration handle. The method loops: get DAG status → find next ready artifact → get instruction → call DirectApiService → write result → invalidate cache → repeat until complete or cancelled.

**Rationale:** Keeps the orchestration logic in the service layer (testable, reusable). The UI just calls start/cancel and observes progress callbacks. The loop re-reads the DAG after each step to respect the real dependency state rather than pre-computing the order.

**Alternative rejected:** Driving the loop from the UI (SwingWorker) — mixes business logic with UI, harder to test.

### Decision 3: Progress reporting

**Choice:** Use a simple callback interface `GenerateAllListener` with methods: `onArtifactStarted(artifactId, index, total)`, `onArtifactCompleted(artifactId)`, `onAllComplete()`, `onError(artifactId, exception)`, `onCancelled(artifactId)`. The WorkflowActionPanel implements this to update the UI.

**Rationale:** Clean separation. The service fires events; the UI renders them. Matches IntelliJ patterns (listener-based).

### Decision 4: Cancellation mechanism

**Choice:** Use `AtomicBoolean` cancellation flag checked between artifacts (not mid-API-call). The UI sets the flag; the orchestration loop checks it before starting the next artifact.

**Rationale:** Simple and safe. Cancelling mid-HTTP-request adds complexity (interrupt handling, partial responses). Between-artifact cancellation is sufficient — each API call is 10-30 seconds, acceptable granularity.

### Decision 5: File writing for specs artifact

**Choice:** The specs artifact uses a glob output path (`specs/**/*.md`). For Generate All, after the API generates specs content, we need to handle directory creation. Reuse the existing `executeGeneration` file-writing logic, extended to handle subdirectory creation when the output path contains slashes.

**Rationale:** The existing DIRECT_API code path in WorkflowActionPanel already writes files. We extract that into a shared utility method in ArtifactOrchestrationService so both single-generate and generate-all can use it.

## Risks / Trade-offs

**Risk:** API rate limiting or timeouts during a 4-artifact chain.
→ Mitigation: Each artifact is an independent API call. If one fails, the chain stops with a clear error. Completed artifacts are preserved. User can retry from where it stopped using the regular Generate button.

**Risk:** Specs artifact output path is a glob (`specs/**/*.md`) — the AI response may need to be split into multiple files.
→ Mitigation: For v1, treat specs as a single file written to `specs/<capability>/spec.md` based on the instruction's context. The existing single-generate already handles this case the same way. Defer multi-file spec splitting to a future enhancement.

**Risk:** User clicks Generate All then navigates away or closes the tool window.
→ Mitigation: Generation runs on a pooled thread. If the panel is disposed, the listener callbacks are no-ops (check `isDisplayable()`). Artifacts still get written to disk.
