## Context

After a change is archived, its delta specs (ADDED/MODIFIED/REMOVED/RENAMED sections) sit in the archive directory but are never applied to the canonical specs under `openspec/specs/`. This means the main specs gradually drift from what was actually implemented. The Sync Specs feature automates this merge so the main specs stay authoritative.

The existing `WorkflowActionPanel` already manages archive and post-archive UI flow. The new Sync Specs action fits naturally between "all artifacts done" and "archive", giving the user a chance to review and apply spec changes before closing out.

## Goals / Non-Goals

**Goals:**
- Parse delta spec markdown sections (ADDED, MODIFIED, REMOVED, RENAMED) into structured operations
- Apply each operation to the corresponding main spec file under `openspec/specs/<capability>/spec.md`
- Show a side-by-side diff preview before applying changes
- Integrate a Sync Specs button into the existing workflow panel

**Non-Goals:**
- Conflict resolution across multiple changes touching the same spec (deferred to Bulk Archive — Phase 2b)
- Automatic spec validation or linting after sync
- Undo/rollback of applied spec syncs (the user can revert via git)

## Decisions

### Decision 1: Markdown section parsing via regex

Parse delta spec sections using heading-level regex (`^## (ADDED|MODIFIED|REMOVED|RENAMED) Requirements`). Each section runs until the next `## ` heading or EOF. Individual requirements are extracted via `### Requirement: <name>` blocks.

**Alternative considered**: Using a Markdown AST parser (commonmark-java). Rejected because the delta format is rigid and well-defined — regex is simpler, has no new dependency, and matches how `VerificationService` already parses task checkboxes.

### Decision 2: SpecSyncService as a project-level service

Create `SpecSyncService` registered via `@Service(Service.Level.PROJECT)` in `plugin.xml`, consistent with all other services in the codebase. The service exposes:
- `parseDeltaSpecs(changeName)` → `List<DeltaSpecOperation>` — reads all `specs/*/spec.md` files in the change directory
- `computeSync(changeName)` → `List<SpecSyncResult>` — computes the before/after content for each affected main spec
- `applySync(List<SpecSyncResult>)` — writes changes via `WriteAction` + VFS refresh

### Decision 3: DiffManager for preview dialog

Use IntelliJ's built-in `DiffManager` with `SimpleDiffRequest` and `DiffContentFactory` to show before/after content for each affected spec. This gives the user a familiar IntelliJ diff experience (syntax highlighting, scroll sync) with zero custom UI work.

**Alternative considered**: Custom `JBTable` with side-by-side text panes. Rejected because `DiffManager` is the platform standard and handles edge cases (long files, encoding) that a custom solution would need to reinvent.

### Decision 4: Operation matching by requirement name

MODIFIED, REMOVED, and RENAMED operations match existing requirements by the `### Requirement: <name>` header text (case-insensitive, whitespace-normalized). A requirement block spans from its `### Requirement:` header to the next `### ` heading or `## ` heading or EOF.

### Decision 5: Button placement in WorkflowActionPanel

The Sync Specs button appears alongside the Verify button when all artifacts are done (the "ready to archive" state). It is visible only when the change has delta specs that differ from the current main specs.

## Risks / Trade-offs

- **[Ambiguous requirement names]** → If a delta spec references a requirement name that doesn't exist in the main spec, MODIFIED/REMOVED/RENAMED will fail. Mitigation: report unmatched operations as warnings in the sync preview, let the user decide whether to proceed.
- **[Partial file creation]** → ADDED operations may need to create a new spec file if the capability is brand new. Mitigation: create `openspec/specs/<capability>/spec.md` with a standard header and purpose section derived from the delta spec.
- **[Ordering sensitivity]** → Multiple operations on the same spec file must be applied in a consistent order (REMOVED first, then RENAMED, then MODIFIED, then ADDED) to avoid header-matching conflicts. Mitigation: sort operations by type before applying.
