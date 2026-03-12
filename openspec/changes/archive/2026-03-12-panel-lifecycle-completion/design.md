## Context

The workflow panel currently guides users through Propose → Generate → Apply but leaves a dead end at completion. When all tasks are marked complete, the panel shows a disabled "All complete" button and fires a notification saying "Consider archiving" — but provides no archive action. Users must navigate to the main menu (OpenSpec → Archive) or tree context menu. With the toolbar-cleanup change, Apply and Archive were removed from the toolbar, making the panel the intended primary surface for change-scoped actions. The panel needs to close the lifecycle loop.

The archive operation already exists in `ChangeService.archiveChange()` (filesystem move) and `ArchiveSyncService.syncAsync()` (tracker reconciliation). Both are called from `OpenSpecArchiveAction` and can be reused directly. Validation exists in `BuiltInValidator` but only at the project level (`validateAll()`, `validateChanges()`) — there's no per-change validation method.

## Goals / Non-Goals

**Goals:**
- Add an "Archive" button to the panel that appears when all tasks are complete
- Execute archive (filesystem + tracker sync) from the panel, showing results inline
- Show a post-archive confirmation state with sync outcomes and a "Start New Change" button
- Add a `validateChange()` method to `BuiltInValidator` for single-change validation
- Auto-validate the active change when all artifacts become DONE and when all tasks complete
- Show validation results in the guidance area

**Non-Goals:**
- No validation indicators on individual pipeline chips (future spec-intelligence work)
- No delta spec sync prompt in the panel (archive from the panel does a direct archive without sync prompt — spec sync is handled by the AI tool during `/opsx:archive`, not by the plugin's built-in archive)
- No changes to the main menu Archive action — it continues to work as-is
- No new archive confirmation dialog — the panel's contextual placement provides sufficient confirmation (user sees the change name, pipeline state, and task count before clicking)

## Decisions

### 1. Reuse existing archive services, don't duplicate logic

**Decision:** Call `ChangeService.archiveChange()` and `ArchiveSyncService.syncAsync()` directly from the panel. Don't extract or refactor the archive action class.

**Rationale:** Both services are already project-level `@Service` components accessible via `project.getService()`. The panel already has a `Project` reference. Adding a dependency on these two services is simpler than extracting a shared helper or making the action class callable.

**Alternative considered:** Extract a `ChangeArchiver` utility class. Rejected — two service calls don't warrant an abstraction.

### 2. Archive button replaces the disabled "All complete" button

**Decision:** When all tasks are complete, instead of showing a disabled "All complete" generate button, show an enabled "Archive" button in the action row with `AllIcons.Actions.MoveTo` icon. The generate button is hidden in this state.

**Rationale:** The "All complete" disabled button is a dead-end affordance — it tells users something is done but provides no next action. Replacing it with Archive gives users an obvious next step. Using the action row (where Generate lives) keeps the layout consistent.

**Alternative considered:** Adding Archive as a separate button below the action row. Rejected — adds visual complexity and breaks the pattern of one primary action per panel state.

### 3. Post-archive state shows results inline, then offers "Start New Change"

**Decision:** After archive completes, the panel transitions to a post-archive state:
- Pipeline chips remain visible (all green, confirming what was archived)
- Guidance area shows: "✓ [change-name] archived" with sync results
- Action row shows a "Start New Change" button that triggers the Propose action
- If sync fails, show the failure with a "Retry Sync" button

**Rationale:** Users need confirmation that the archive worked and what happened with trackers. Showing "Start New Change" creates a natural loop back to the beginning of the lifecycle. The pipeline chips stay visible so the user has context about what they just shipped.

### 4. Extract validateChange() from existing validateChanges() loop

**Decision:** Add a public `validateChange(String changeName)` method to `BuiltInValidator` that validates a single change. Refactor `validateChanges()` to call this method in its loop.

**Rationale:** The existing `validateChanges()` already validates each change individually in a for-loop. Extracting the per-change logic into its own method is mechanical — just move the loop body into a new method. This gives the panel a clean API to call.

### 5. Auto-validate at phase transitions, show results in guidance

**Decision:** Trigger `validateChange()` at two phase transition points:
1. When all artifacts become DONE (entering Build phase) — validated in `showApplyState()`
2. When all tasks complete (entering Ship phase) — validated in `onTaskFileChanged()` when task count reaches total

Show results in the guidance area: "✓ All artifacts valid" or "⚠ 1 warning: [description]". Validation warnings don't block — the user can proceed with Apply or Archive regardless.

**Rationale:** These are the natural checkpoints where users pause and look at the panel. Validating here catches issues at the right moment without requiring a manual action. Non-blocking warnings match IntelliJ's philosophy — inform, don't obstruct.

**Alternative considered:** Adding a dedicated Validate button to the panel. Rejected — adds button clutter. The toolbar's Validate All button serves the explicit-validation use case; the panel should validate automatically at the right moments.

### 6. Run validation on a background thread

**Decision:** Call `validateChange()` via `ApplicationManager.getApplication().executeOnPooledThread()` and update the UI on the EDT via `SwingUtilities.invokeLater()`.

**Rationale:** Validation reads files from the VFS and does regex matching. While typically fast, it shouldn't block the EDT. This matches the pattern used by `ArchiveSyncService.syncAsync()`.

## Risks / Trade-offs

**[Risk] Archive from panel has no confirmation dialog** → The user sees the change name, all-green pipeline, and "All tasks complete" before the Archive button appears. This provides sufficient context. If users report accidental archives, a lightweight confirmation can be added later.

**[Risk] Auto-validation adds latency to phase transitions** → Validation is file-reads + regex, typically <100ms. Running on a pooled thread prevents EDT blocking. The guidance area can show "Validating..." briefly if needed.

**[Trade-off] No delta spec sync prompt in panel** → The built-in Archive action doesn't handle spec sync (that's the AI tool's responsibility via `/opsx:archive`). This means archiving from the panel moves files and updates trackers but doesn't sync delta specs to main specs. Users who want spec sync should use their AI tool's archive command. This is an acceptable trade-off for v0.1.0 — the panel provides the common path; the AI tool provides the full workflow.

**[Trade-off] Validation results in guidance area, not on chips** → Chip-level validation indicators would be ideal but add significant complexity (overlay icons, tooltip integration, chip state machine changes). Guidance area validation is simpler and sufficient for v0.1.0. Chip-level indicators align with the v0.2.0 spec-intelligence roadmap.
