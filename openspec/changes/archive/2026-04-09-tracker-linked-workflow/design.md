## Context

The OpenSpec workflow uses Claude Code skills (SKILL.md files) to orchestrate change lifecycle: propose, new, continue, apply, verify, archive. These skills are mirrored across 5 agent directories (`.claude/`, `.augment/`, `.codex/`, `.gemini/`, `.github/`). Forgejo (git hosting + issues) and Plane (project management + work items) are the external trackers, accessed via REST APIs with tokens in `scripts/.env`.

Currently, tracker updates are manual and happen post-hoc. The goal is to make them automatic at the natural lifecycle boundaries: creation and archival.

## Goals / Non-Goals

**Goals:**
- Auto-create Forgejo issue + Plane work item when a change is created
- Store tracker IDs in `.openspec.yaml` so archive can find them
- Auto-close/complete trackers when a change is archived
- Validate tracker + release state before tagging via `/release-prep`
- Keep it simple — inline curl calls in skills, no new dependencies

**Non-Goals:**
- Building a general-purpose tracker abstraction — this is specific to Forgejo + Plane
- Modifying the OpenSpec CLI or plugin code
- Making tracker integration mandatory — skills should gracefully skip if tokens are missing
- Syncing tracker state during implementation (only at creation and archival)

## Decisions

### D1: Inline API calls in skills, not a shared script

**Choice**: Each skill includes the curl commands directly in its instructions rather than referencing a shared helper script.

**Why**: Skills are self-contained markdown instruction files. Referencing external scripts creates a dependency that may not resolve correctly across different agent runtimes. Inline curl is portable and debuggable.

**Trade-off**: Some duplication across propose/new/archive skills. Acceptable given the small number of API calls (2 per lifecycle event).

### D2: Store tracking IDs in `.openspec.yaml`

**Choice**: Add a `tracking` section to the change's `.openspec.yaml`:
```yaml
tracking:
  forgejo_issue: 187
  plane_work_item: f5681bc0-2c9f-4a20-8d80-dbc0e2e2652f
```

**Why**: `.openspec.yaml` already exists for every change and moves to the archive with the change. No new files needed. The archive skill already reads this file for change metadata.

### D3: Graceful degradation when tokens are missing

**Choice**: Skills check for `scripts/.env` and required tokens before making API calls. If missing, log a note ("Tracker integration skipped — scripts/.env not found") and continue without failing.

**Why**: The public repo doesn't have tracker credentials. Contributors who clone the repo shouldn't have their workflow blocked by missing tokens.

### D4: `/release-prep` as a standalone skill

**Choice**: New skill at `.claude/skills/release-prep/SKILL.md` that runs a sequential checklist.

**Why**: Release prep is a distinct workflow action, not part of propose/archive. It needs its own entry point. It orchestrates multiple checks (version, changelog, build, changes, trackers) and presents results before the user decides to tag.

### D5: Auto-create milestones/cycles in release-prep

**Choice**: If the release version doesn't have a Forgejo milestone or Plane cycle, `/release-prep` creates them and reassigns relevant issues from the catch-all milestone (v0.3.0).

**Why**: Per-release milestones keep tracking accurate. Creating them at release-prep time (not at change creation) avoids premature milestone proliferation for versions that may never ship.

### D6: Labels and priority inferred from proposal

**Choice**: The propose/new skill reads the proposal content to determine labels (bug/enhancement/infrastructure) and priority (based on keywords like "critical", "deadlock", "fix").

**Why**: This avoids prompting the user for metadata they've already described in the proposal. Simple keyword matching is sufficient — this isn't a classifier.

## Risks / Trade-offs

- **API availability**: If Forgejo or Plane is down, the skill reports the error and continues. Tracker creation is best-effort, not blocking.
- **Stale tracking IDs**: If someone manually closes the Forgejo issue or Plane work item before archiving, the archive skill's close call is a no-op (APIs are idempotent for state transitions).
- **Multi-agent duplication**: The same skill content is mirrored to 5 agent directories. Changes must be applied to all. This is existing technical debt, not introduced by this change.
