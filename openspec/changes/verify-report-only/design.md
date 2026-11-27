# Design

## Context

`VerifyDialog` renders `ArchiveReadinessResult` in three states (READY, IN PROGRESS, BLOCKED). Three callers open it and archive on OK: `OpenSpecVerifyAction`, `WorkflowActionPanel.onVerify` and `OpenSpecArchiveAction`'s pre-flight. Upstream's `verify` workflow template forbids archiving during verification; archiving is the `archive` workflow's job, and a custom profile may hold `verify` without `archive`.

## Goals / Non-Goals

**Goals:**
- Verify never archives by itself, on any profile.
- Keep a one-step path from a ready Verify report to archiving, only where `archive` is offered.
- Say on the button what it does.

**Non-Goals:**
- Changing the readiness check, its states or its findings.
- Verify's semantic (AI) assessment, or a hand-off to the agent's verify skill (a separate tracker entry).

## Decisions

### D1. Two forms of one dialog

`VerifyDialog` takes a form:
- **REPORT** (Verify menu action, tool-window Verify button). The OK button reads **Close**, is always enabled, and closes. When the caller allows archiving, an extra **Archive…** action is added: "Archive…" for READY, "Archive anyway…" for IN PROGRESS, disabled for BLOCKED. It closes the dialog with a distinct exit code.
- **ARCHIVE_PREFLIGHT** (Archive action). Unchanged: OK reads "Archive" or "Archive anyway", disabled for BLOCKED.

The labels and enablement are pure functions of form, state and the archive flag, so they are unit-tested without an IDE.

### D2. The archive gate is the profile

The Verify callers pass `WorkflowProfileService.isWorkflowEnabled("archive")`. With no profile service, as in a project without OpenSpec configured, archiving is offered, matching how `OpenSpecBaseAction` treats a missing service. The Archive… exit code then runs the caller's existing archive step: `OpenSpecVerifyAction.archiveChange` and `WorkflowActionPanel.onArchive`. Neither re-runs the readiness check, because the user has just seen it.

### D3. Wording

The header and findings stay as they are. "Ready to archive" remains a statement of readiness, not an action; the button that archives says "Archive…".

## Risks / Trade-offs

- **One more click for users who used Verify to archive.** Accepted: Archive… is one click and says what it does.
- **The tool window's open-task confirmation runs after "Archive anyway…".** `onArchive` asks again when tasks are open. Mitigation: pass the confirmation as already given when the hand-off came from "Archive anyway…".

## Migration Plan

None.

## Open Questions

None.
