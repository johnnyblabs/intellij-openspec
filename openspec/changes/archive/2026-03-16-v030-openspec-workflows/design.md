## Context

The plugin currently implements 4 workflows (propose, apply, archive, explore-as-clipboard) covering the OpenSpec "core" profile. The OpenSpec CLI's expanded profile adds 6 more workflows that power users rely on: ff, continue, verify, sync, bulk-archive, and new (step-by-step). Additionally, the CLI supports custom workflow schemas, config profiles, and an `update` command.

The existing architecture follows a clear pattern: `AnAction` → service layer → CLI delegation (with built-in fallback) → UI update. All write operations go through `WriteAction` + VFS refresh. The `WorkflowActionPanel` provides the primary interaction surface with artifact pipeline visualization.

## Goals / Non-Goals

**Goals:**
- Full parity with OpenSpec CLI expanded workflow profile
- All new workflows accessible from menu, toolbar, and workflow action panel
- Consistent UX patterns: same action/service/CLI architecture as existing workflows
- Custom schema support for teams with non-standard artifact pipelines
- Pre-archive verification to catch incomplete implementations

**Non-Goals:**
- Terminal-based interactive dashboard (`openspec view`) — IntelliJ's tool window serves this purpose
- Chat Participant API integration — VS Code-only capability, no IntelliJ equivalent
- Ollama/local AI provider support — deferred to v0.4.0
- Post-archive tracker automation (Forgejo/Plane) — separate change

## Decisions

### Decision 1: FF as a dialog-driven workflow, not a panel button

FF (Fast-Forward) combines change creation + full artifact generation. It needs user input (description of what to build) before it can start. Rather than adding a button to the existing workflow panel, FF gets its own dialog — similar to ProposeChangeDialog but with a "Generate Everything" mindset.

**Why over alternative (panel button):** A panel button would require selecting an existing change, but FF creates a new one. The dialog captures the user's intent upfront and shows generation progress inline.

**Implementation:**
- `OpenSpecFfAction` → opens `FfDialog` (name + description input)
- Dialog derives kebab-case name, calls `openspec new change "<name>"`
- Then runs the same DAG-walking generation loop as "Generate All" in `ArtifactOrchestrationService`
- Progress shown in dialog with artifact checklist
- On completion: refreshes tool window, selects the new change, shows summary

### Decision 2: Continue as a one-click panel action

Continue is the simplest workflow: find the next ready artifact, generate it. This belongs as a button in the `WorkflowActionPanel` next to the existing Generate button.

**Why over alternative (separate dialog):** Continue is a "resume" action — the context (which change, which artifact) is already established by the change selector and pipeline visualization. No additional input needed.

**Implementation:**
- New "Continue" button in workflow panel (icon: play/resume)
- Calls `ArtifactOrchestrationService.getNextReadyArtifact(changeName)`
- Generates the single artifact via the existing delivery method
- Updates pipeline visualization
- If all artifacts complete, shows "All artifacts done — ready to apply" message

### Decision 3: Verify as a results panel with report

Verify produces structured output (CRITICAL/WARNING/SUGGESTION findings). This needs more than a notification — it needs a results view.

**Why over alternative (notification only):** Verification can produce 10+ findings. A notification would be unreadable. A results panel (similar to IntelliJ's "Problems" view) lets users navigate findings.

**Implementation:**
- `OpenSpecVerifyAction` → runs `VerificationService.verify(changeName)`
- `VerificationService` checks three dimensions:
  - **Completeness**: task checkbox parsing (`- [x]` vs `- [ ]`), artifact presence
  - **Correctness**: delta spec requirements searched in codebase via grep/PSI
  - **Coherence**: design decisions cross-referenced with implementation
- Results displayed in a new "Verification" tab in the tool window (or a modal report dialog for simplicity in v0.3.0)
- Each finding: severity icon + description + file link
- Summary: "3 critical, 2 warnings, 1 suggestion — NOT ready to archive" or "All clear — ready to archive"

### Decision 4: Sync Specs as a CLI-delegated action with preview

Spec sync modifies main spec files — this is a destructive operation that merits a preview step.

**Why over alternative (auto-sync):** Blindly merging delta specs could clobber hand-edited main specs. Users need to see what will change before committing.

**Implementation:**
- `OpenSpecSyncAction` → shows preview dialog with diff view
- `SpecSyncService` parses delta specs (ADDED/MODIFIED/REMOVED/RENAMED sections)
- Preview dialog shows side-by-side diff of main spec before/after
- User confirms → service applies changes via `WriteAction`
- Falls back to manual merge guidance if delta spec format is ambiguous

### Decision 5: Bulk Archive as a multi-select dialog

**Implementation:**
- `OpenSpecBulkArchiveAction` → opens dialog listing active changes with checkboxes
- Shows per-change status (task completion %, artifact completeness)
- Detects conflicts: highlights when multiple selected changes touch the same spec domain
- User confirms → archives sequentially, running spec sync for each
- Summary notification: "Archived 3 changes, 1 conflict resolved"

### Decision 6: Custom Schemas via Settings panel

Schema management is a configuration concern, not a frequent workflow action. It belongs in Settings.

**Implementation:**
- New "Schemas" section in OpenSpec Settings panel
- Lists available schemas (from `openspec schemas --json`)
- "Fork" button: calls `openspec schema fork <source> <name>` → opens forked schema in editor
- "New" button: calls `openspec schema init <name>` with artifact selection dialog
- Default schema selector: stored in `config.yaml`
- Change creation (Propose, FF, New) offers schema picker when multiple schemas exist

### Decision 7: Enhanced Explore as a tool window tab

Replace the clipboard-only ExploreContextAction with an interactive panel.

**Implementation:**
- New "Explore" tab in OpenSpec tool window (alongside Browse, Coverage, Console)
- Shows assembled project context: config summary, active changes, spec overview, detected tools
- "Copy to Clipboard" button (preserves existing functionality)
- "Open in Editor" button (opens context as a scratch file for editing before pasting)
- Search/filter within the context view
- Auto-refreshes on project changes

### Decision 8: Implementation phasing

Given the scope, implement in 3 phases within v0.3.0:

**Phase 1 — Core workflows** (highest user impact):
- FF (Fast-Forward)
- Continue
- Verify

**Phase 2 — Spec management**:
- Sync Specs
- Bulk Archive

**Phase 3 — Configuration & polish**:
- Custom Schemas
- Enhanced Explore
- Config Profile management
- Update CLI instructions

## Risks / Trade-offs

- **[Risk] Large scope for a single version** → Mitigated by 3-phase implementation. Each phase is independently shippable. If v0.3.0 ships with only Phase 1, it's still a meaningful release.
- **[Risk] CLI version compatibility** → The `schema` commands are marked `[experimental]` in the CLI. API may change. → Mitigate by wrapping CLI calls in version-checking guards and providing built-in fallbacks.
- **[Risk] Verify correctness is heuristic-based** → Searching codebase for spec requirement keywords is imprecise. → Start with task completion checking (deterministic) and make codebase search best-effort with clear "confidence" indicators.
- **[Risk] Sync Specs could clobber manual edits** → Mitigated by preview dialog with diff view. User must confirm before changes are applied.
- **[Trade-off] Explore panel vs clipboard simplicity** → Adding a panel increases UI complexity. → Keep the one-click clipboard action as the primary path; the panel is opt-in for users who want to review context first.
