## 1. Phase 1 — Fast-Forward (FF)

- [x] 1.1 Create `FfDialog` with description field, optional name override, schema selector, and progress panel
- [x] 1.2 Implement name derivation logic (description → kebab-case, e.g., "add user auth" → "add-user-auth")
- [x] 1.3 Create `OpenSpecFfAction` that opens `FfDialog` and delegates to `ArtifactOrchestrationService`
- [x] 1.4 Wire FF to call `openspec new change "<name>"` then run DAG-walking generation loop
- [x] 1.5 Add progress feedback in dialog (artifact checklist with done/generating/blocked states)
- [x] 1.6 Register `OpenSpec.Ff` action in `plugin.xml` with menu item and toolbar button
- [x] 1.7 Add tests for name derivation and FF action flow

## 2. Phase 1 — Continue

- [x] 2.1 Add `getNextReadyArtifact(changeName)` method to `ArtifactOrchestrationService`
- [x] 2.2 Create `OpenSpecContinueAction` that finds next ready artifact and triggers generation
- [x] 2.3 Add Continue button to `WorkflowActionPanel` alongside existing Generate/Generate All buttons
- [x] 2.4 Handle edge cases: all artifacts complete, no change selected, no ready artifacts
- [x] 2.5 Register `OpenSpec.Continue` action in `plugin.xml`
- [x] 2.6 Add tests for next-artifact detection and Continue action

## 3. Phase 1 — Verify

- [x] 3.1 Create `VerificationService` with three check dimensions (completeness, correctness, coherence)
- [x] 3.2 Implement completeness checks: task checkbox parsing, artifact presence verification
- [x] 3.3 Implement correctness checks: search codebase for delta spec requirement keywords
- [x] 3.4 Implement coherence checks: cross-reference design decisions against implementation
- [x] 3.5 Create verification report model with CRITICAL/WARNING/SUGGESTION severity levels
- [x] 3.6 Create `VerifyReportDialog` displaying findings grouped by dimension with file links
- [x] 3.7 Create `OpenSpecVerifyAction` and add Verify button to `WorkflowActionPanel`
- [x] 3.8 Register `OpenSpec.Verify` action in `plugin.xml`
- [x] 3.9 Add tests for each verification dimension

## 4. Phase 2 — Sync Specs

- [ ] 4.1 Create `SpecSyncService` that parses delta spec sections (ADDED/MODIFIED/REMOVED/RENAMED)
- [ ] 4.2 Implement ADDED: append requirements to main spec (create file if needed)
- [ ] 4.3 Implement MODIFIED: locate and replace matching requirement blocks
- [ ] 4.4 Implement REMOVED: remove matching requirement blocks
- [ ] 4.5 Implement RENAMED: update requirement headers (FROM:/TO: format)
- [ ] 4.6 Create `SyncPreviewDialog` with side-by-side diff view of main specs before/after
- [ ] 4.7 Create `OpenSpecSyncAction` and add Sync Specs button to `WorkflowActionPanel`
- [ ] 4.8 Register `OpenSpec.SyncSpecs` action in `plugin.xml`
- [ ] 4.9 Add tests for each sync operation type

## 5. Phase 2 — Bulk Archive

- [ ] 5.1 Create `BulkArchiveDialog` with checkbox list of active changes and per-change status
- [ ] 5.2 Implement conflict detection: identify when multiple changes touch the same spec domain
- [ ] 5.3 Implement sequential archive with spec sync for each change
- [ ] 5.4 Create `OpenSpecBulkArchiveAction`
- [ ] 5.5 Register `OpenSpec.BulkArchive` action in `plugin.xml`
- [ ] 5.6 Add tests for conflict detection and batch archive flow

## 6. Phase 3 — Custom Schemas

- [ ] 6.1 Create `SchemaService` wrapping `openspec schemas --json`, `schema fork`, and `schema init`
- [ ] 6.2 Add Schemas section to OpenSpec Settings panel with schema list and default selector
- [ ] 6.3 Add "Fork" button that runs `openspec schema fork` and opens forked schema in editor
- [ ] 6.4 Add "New Schema" dialog with name, description, and artifact selection fields
- [ ] 6.5 Add schema dropdown to ProposeChangeDialog and FfDialog (visible when multiple schemas exist)
- [ ] 6.6 Add tests for schema service CLI interactions

## 7. Phase 3 — Enhanced Explore

- [ ] 7.1 Create `ExplorePanel` as a new tab in the OpenSpec tool window
- [ ] 7.2 Implement context assembly: config summary, active changes, spec domains, detected tools
- [ ] 7.3 Add "Copy to Clipboard" button (preserving existing ExploreContextAction behavior)
- [ ] 7.4 Add "Open in Editor" button that opens context as a scratch file
- [ ] 7.5 Implement auto-refresh on `openspec/` file changes via VFS listener
- [ ] 7.6 Add tests for context assembly

## 8. Phase 3 — Config & CLI Management

- [ ] 8.1 Add config profile section to OpenSpec Settings (display current profile, workflow toggles)
- [ ] 8.2 Implement profile switch via `openspec config profile` command delegation
- [ ] 8.3 Create `OpenSpecUpdateAction` that runs `openspec update` with console output
- [ ] 8.4 Register `OpenSpec.Update` action in `plugin.xml` (disabled when CLI not detected)
- [ ] 8.5 Add tests for config profile and update actions

## 9. Integration & Polish

- [ ] 9.1 Update getting started panel to mention Fast-Forward as an alternative to Propose
- [ ] 9.2 Add keyboard shortcuts for FF, Continue, and Verify actions
- [ ] 9.3 Update feature comparison matrix with new capabilities
- [ ] 9.4 Update plugin description for JetBrains Marketplace listing
- [ ] 9.5 Run full test suite and fix any regressions
