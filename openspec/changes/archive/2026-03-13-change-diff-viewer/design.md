## Context

Delta spec files live at `openspec/changes/<name>/specs/<domain>/spec.md` and their corresponding main specs at `openspec/specs/<domain>/spec.md`. The `DELTA_SPEC` tree node already stores the delta file path and domain name can be derived from the path. Currently there are no context menu actions for DELTA_SPEC nodes.

IntelliJ provides `DiffManager.getInstance().showDiff()` with `SimpleDiffRequest` and `DiffContentFactory` for showing side-by-side diffs in a standard diff viewer window.

## Goals / Non-Goals

**Goals:**
- "Preview Diff" action shows delta spec vs main spec in IntelliJ's diff viewer
- "Open File" action opens the delta spec in the editor
- Graceful handling when the main spec doesn't exist yet (new capability)

**Non-Goals:**
- Applying delta changes to main specs from the diff viewer (that's the archive/sync workflow)
- Three-way merge or conflict resolution
- Inline diff annotations in the editor

## Decisions

### Decision 1: Use IntelliJ's built-in DiffManager

Use `DiffManager.getInstance().showDiff(project, request)` with `SimpleDiffRequest` containing two `DiffContent` objects created via `DiffContentFactory`. This gives us the standard IntelliJ diff UI for free — syntax highlighting, navigation, unified/side-by-side toggle.

**Alternative considered:** Custom split editor panel. Rejected — reinventing what IntelliJ already provides, and users already know the diff viewer UX.

### Decision 2: Derive main spec path from delta spec path

Extract the domain name from the delta spec path by parsing `openspec/changes/<name>/specs/<domain>/spec.md` and mapping to `openspec/specs/<domain>/spec.md`. This keeps the action self-contained — no need for additional service methods.

### Decision 3: Show empty content when main spec doesn't exist

For new capabilities (no main spec yet), show the delta spec on the right with an empty left panel labeled "New capability — no existing spec". This makes it clear the entire delta will be added as a new spec.

## Risks / Trade-offs

- **[Risk] Path parsing fragility** → Mitigated by using `OpenSpecFileUtil` methods for OpenSpec directory detection rather than string manipulation. The domain name is the parent directory of `spec.md`.
- **[Risk] Large spec files in diff viewer** → Non-issue, spec files are typically under 200 lines. IntelliJ diff handles much larger files.
