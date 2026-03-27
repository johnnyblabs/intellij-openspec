## Context

`DeltaSpecInspection` currently passes `(LocalQuickFix) null` for all three error types (missing sections, missing REMOVED metadata, missing MODIFIED scenarios). The MODIFIED-missing-scenarios case is the best candidate for a quick-fix because the fix is mechanical: copy the full requirement from the main spec so the user can edit it.

## Goals / Non-Goals

**Goals:**
- Provide a one-click fix for MODIFIED requirements missing scenarios
- Resolve the capability name from the delta spec's file path and read the corresponding main spec
- Insert the full requirement block (description + all scenarios) after the `### Requirement:` line

**Non-Goals:**
- Quick-fixes for ADDED requirements (no main spec content to copy)
- Quick-fixes for REMOVED requirements (needs Reason/Migration, not requirement content)
- Automatic diff or merge between delta and main spec content

## Decisions

### Inner class LocalQuickFix in DeltaSpecInspection

Implement the quick-fix as a private static inner class `CopyRequirementFromMainSpec` inside `DeltaSpecInspection`. It receives the capability name and requirement name at construction time.

**Why not a separate class?** The fix is small and tightly coupled to the inspection logic. An inner class keeps it co-located.

### Use VFS to read the main spec

The quick-fix resolves the main spec at `openspec/specs/<capability>/spec.md` via `OpenSpecFileUtil.getOpenSpecRoot()` + VFS traversal. This reuses existing infrastructure and works within the IntelliJ read-action model.

### Replace the entire requirement line through end-of-block

The quick-fix finds the `### Requirement: <name>` line in the delta spec and replaces from there to the next `###` heading (or end of section) with the full requirement block from the main spec. This ensures the user gets complete, up-to-date content.

## Risks / Trade-offs

- **Requirement name mismatch**: If the delta spec's requirement name doesn't exactly match the main spec, the lookup fails silently and the fix won't be offered. Acceptable — the user can still copy manually.
- **Overwrites user content**: If the user has partial content under the requirement, the fix replaces it. Acceptable — this is the same behavior as IntelliJ's other quick-fixes, and undo is available.
