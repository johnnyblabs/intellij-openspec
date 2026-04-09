## Why

Forgejo issues and Plane work items are created after implementation is done — or forgotten entirely. The tracker state drifts from the actual work. During the v0.2.9 cycle, we had to retroactively create issues for two completed changes. Release prep (closing issues, moving work items to Done, creating milestones) is manual and error-prone. There is no pre-tag validation that trackers are in sync.

## What Changes

- **Tracker creation at change inception**: `/opsx:propose` and `/opsx:new` skills auto-create a Forgejo issue + Plane work item after creating the change, store IDs in `.openspec.yaml` `tracking` metadata, and cross-link them
- **Tracker closure on archive**: `/opsx:archive` skill reads tracking metadata from `.openspec.yaml` and auto-closes the Forgejo issue + moves the Plane work item to Done
- **New `/release-prep` skill**: validates version, changelog entry, build, archived changes, closed Forgejo issues, and Done Plane work items before tagging — auto-creates milestones/cycles for the release version and reassigns relevant issues from the catch-all milestone
- **Shared tracker helper**: a reusable shell script or inline instructions that all skills reference for Forgejo/Plane API calls using `scripts/.env` credentials

## Capabilities

### New Capabilities
- `tracker-integration`: Automated Forgejo issue and Plane work item lifecycle tied to OpenSpec change creation and archival
- `release-prep`: Pre-tag validation checklist covering version, changelog, build, changes, and tracker state

### Modified Capabilities
_(none — this changes skill files, not plugin specs)_

## Impact

- **Files changed**: `.claude/skills/openspec-propose/SKILL.md`, `.claude/skills/openspec-new-change/SKILL.md`, `.claude/skills/openspec-archive-change/SKILL.md`, plus mirrored files in `.augment/`, `.codex/`, `.gemini/`, `.github/` (55 skill dirs total, 3 modified + 1 new per agent = ~20 files)
- **New file**: `.claude/skills/release-prep/SKILL.md` (+ mirrors)
- **Dependencies**: `scripts/.env` (Forgejo/Plane tokens), `scripts/personal/lib/` API helpers (existing)
- **No plugin code changes** — all changes are to Claude Code skill instruction files
