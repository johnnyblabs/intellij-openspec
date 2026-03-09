# Tasks: generate-all-button

## 1. GenerateAllListener callback interface

- [x] 1.1 Create `GenerateAllListener` interface in `services/` with methods: `onArtifactStarted(artifactId, index, total)`, `onArtifactCompleted(artifactId)`, `onAllComplete()`, `onError(artifactId, Exception)`, `onCancelled(artifactId)`
- [x] 1.2 Verify the interface compiles cleanly

## 2. ArtifactOrchestrationService — generateAllRemaining

- [x] 2.1 Add `generateAllRemaining(changeName, DirectApiService, GenerateAllListener)` method that loops: get DAG → find next ready → get instruction → call API → write file → invalidate cache → repeat
- [x] 2.2 Add `AtomicBoolean` cancellation flag, checked between artifacts
- [x] 2.3 Add `cancelGenerateAll()` method that sets the flag
- [x] 2.4 Handle specs glob output path — create subdirectory if output path contains `/`
- [x] 2.5 Re-read DAG after each artifact write to respect dependency state
- [x] 2.6 Fire listener callbacks at each stage (started, completed, allComplete, error, cancelled)

## 3. WorkflowActionPanel — Generate All button

- [x] 3.1 Add `generateAllButton` field and create it in the constructor with label "Generate All"
- [x] 3.2 Add the button to the button panel (next to existing Generate + dropdown)
- [x] 3.3 Wire `generateAllButton` action listener to `onGenerateAll()` method
- [x] 3.4 In `updatePipelineAndButton()`: show Generate All only when Direct API is configured AND 2+ artifacts remain
- [x] 3.5 Hide (not disable) the button when conditions aren't met

## 4. WorkflowActionPanel — Generate All execution and progress

- [x] 4.1 Add `onGenerateAll()` method that starts orchestration on pooled thread
- [x] 4.2 Implement `GenerateAllListener` in WorkflowActionPanel
- [x] 4.3 On `onArtifactStarted`: update Generate button text to "Generating [id]... N/M", disable both Generate buttons, show Cancel button
- [x] 4.4 On `onArtifactCompleted`: refresh pipeline chips to show the completed artifact as done
- [x] 4.5 On `onAllComplete`: restore normal state, refresh panel, show "All complete" notification
- [x] 4.6 On `onError`: restore normal state, show error notification with failed artifact name
- [x] 4.7 On `onCancelled`: restore normal state, refresh panel showing partial progress

## 5. WorkflowActionPanel — Cancel button

- [x] 5.1 Add `cancelButton` field, create it with label "Cancel"
- [x] 5.2 Initially hidden; shown when Generate All is in progress
- [x] 5.3 Wire cancel action to call `orchestrationService.cancelGenerateAll()`
- [x] 5.4 After cancel, hide cancel button and restore Generate buttons

## 6. Integration and testing

- [x] 6.1 Verify Generate All works end-to-end with a test change using Direct API (manual)
- [x] 6.2 Verify cancellation stops the chain and preserves completed artifacts
- [x] 6.3 Verify error on one artifact stops chain with clear notification
- [x] 6.4 Verify Generate All button is hidden when Direct API is not configured
- [x] 6.5 Verify Generate All button is hidden when only 1 artifact remains
- [x] 6.6 Run full test suite: `./gradlew clean build test` — all green
